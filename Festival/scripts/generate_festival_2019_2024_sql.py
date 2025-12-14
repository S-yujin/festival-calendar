import argparse
import math
import re
from pathlib import Path
from datetime import datetime
import pandas as pd


# =========================
# 공통 유틸
# =========================
def is_nan(x) -> bool:
    return x is None or (isinstance(x, float) and math.isnan(x))


def s(x) -> str:
    """문자 정리(빈값 -> ''), NaN/None 안전 처리"""
    if is_nan(x):
        return ""
    return str(x).strip()


def esc_sql(x) -> str:
    """SQL 문자열 이스케이프 (작은따옴표)"""
    return s(x).replace("'", "''")


def parse_yyyymmdd(x):
    """
    20200131 / '20200131' / '2020-01-31' / '2020.01.31' -> 'YYYY-MM-DD'
    실패/빈값 -> None
    """
    if is_nan(x):
        return None
    txt = s(x)
    if not txt:
        return None

    digits = re.sub(r"[^0-9]", "", txt)
    if len(digits) >= 8:
        digits = digits[:8]
        try:
            dt = datetime.strptime(digits, "%Y%m%d")
            return dt.strftime("%Y-%m-%d")
        except ValueError:
            return None
    return None


def date_literal(x):
    """YYYY-MM-DD or None -> SQL 리터럴(날짜)"""
    d = parse_yyyymmdd(x)
    return "NULL" if d is None else f"'{d}'"


def detect_year_from_path(p: Path):
    m = re.search(r"(19|20)\d{2}", p.name)
    if m:
        return int(m.group(0))
    m2 = re.search(r"(19|20)\d{2}", str(p.parent))
    if m2:
        return int(m2.group(0))
    return None


def read_csv_safely(path: Path) -> pd.DataFrame:
    """
    KC_488 계열: utf-8-sig 인 경우가 많고,
    2024 변환본은 euc-kr/cp949일 수 있어서 순차 시도.
    """
    encodings = ["utf-8-sig", "utf-8", "euc-kr", "cp949"]
    last_err = None
    for enc in encodings:
        try:
            return pd.read_csv(path, encoding=enc)
        except Exception as e:
            last_err = e
    raise last_err


def ensure_cols(df: pd.DataFrame, required: list, context: str):
    missing = [c for c in required if c not in df.columns]
    if missing:
        raise ValueError(f"[ERROR] {context} 에 필요한 컬럼이 없음: {missing}")


# =========================
# 로더 (2019~2024 CSV 통합)
# =========================
def load_kc488_csv(path: Path) -> pd.DataFrame:
    df = read_csv_safely(path)

    # KC_488 기본 컬럼 기대치
    required = [
        "ID",
        "LCLAS_NM",
        "MLSFC_NM",
        "FCLTY_NM",
        "CTPRVN_NM",
        "SIGNGU_NM",
        "FSTVL_BEGIN_DE",
        "FSTVL_END_DE",
    ]
    ensure_cols(df, required, f"CSV({path.name})")

    year = detect_year_from_path(path)

    # raw_id 충돌 방지: 연도 + 원본 ID
    def build_raw_id(row):
        rid = s(row.get("ID"))
        y = year
        if y is None:
            start = parse_yyyymmdd(row.get("FSTVL_BEGIN_DE"))
            y = int(start[:4]) if start else 0
        return f"KC488-{y}-{rid}"

    df["RAW_ID_GEN"] = df.apply(build_raw_id, axis=1)

    # master 쪽 필드
    df["fstvl_nm"] = df["FCLTY_NM"].apply(s)
    df["ctprvn_nm"] = df["CTPRVN_NM"].apply(s)
    df["signgu_nm"] = df["SIGNGU_NM"].apply(s)

    df["legaldong_nm"] = df["LEGALDONG_NM"].apply(s) if "LEGALDONG_NM" in df.columns else ""
    df["adstrd_nm"] = df["ADSTRD_NM"].apply(s) if "ADSTRD_NM" in df.columns else ""
    df["zip_no"] = df["ZIP_NO"].apply(s) if "ZIP_NO" in df.columns else ""
    df["addr1"] = df["RDNMADR_NM"].apply(s) if "RDNMADR_NM" in df.columns else ""

    df["tel_no"] = df["TEL_NO"].apply(s) if "TEL_NO" in df.columns else ""
    df["hmpg_addr"] = df["HMPG_ADDR"].apply(s) if "HMPG_ADDR" in df.columns else ""

    # 좌표
    df["mapx"] = df["FCLTY_LO"] if "FCLTY_LO" in df.columns else None
    df["mapy"] = df["FCLTY_LA"] if "FCLTY_LA" in df.columns else None

    # event 쪽 필드(스키마 유지용으로 필요한 것만)
    df["raw_id"] = df["RAW_ID_GEN"]
    df["start_date"] = df["FSTVL_BEGIN_DE"]
    df["end_date"] = df["FSTVL_END_DE"]

    df["origin_nm"] = df["ORIGIN_NM"].apply(s) if "ORIGIN_NM" in df.columns else "KC_488_WNTY_CLTFSTVL"
    df["data_base_de"] = df["BASE_DE"] if "BASE_DE" in df.columns else None

    # 마스터에 없는 필드들은 빈값/NULL
    df["first_image_url"] = ""
    df["overview"] = ""
    df["tourapi_content_id"] = None

    keep = [
        # master
        "fstvl_nm", "ctprvn_nm", "signgu_nm",
        "legaldong_nm", "adstrd_nm", "zip_no", "addr1",
        "tel_no", "hmpg_addr", "mapx", "mapy",
        "first_image_url", "overview", "tourapi_content_id",
        # event
        "raw_id",
        "start_date", "end_date",
        "origin_nm", "data_base_de",
    ]
    return df[keep].copy()


# =========================
# SQL 생성 (현재 스키마 유지 버전)
# =========================
def build_master_insert_sql(df_master: pd.DataFrame) -> list[str]:
    """
    [현재 스키마 유지]
    - festival_master: UNIQUE가 없다고 가정하고, (fstvl_nm, ctprvn_nm, signgu_nm) 조합이 없을 때만 INSERT
    - updated_at 같은 컬럼 사용 안 함
    """
    lines = []

    dfu = df_master.drop_duplicates(subset=["fstvl_nm", "ctprvn_nm", "signgu_nm"]).copy()

    def num_or_null(x):
        if is_nan(x) or s(x) == "":
            return "NULL"
        try:
            return str(float(x))
        except Exception:
            return "NULL"

    for _, r in dfu.iterrows():
        fstvl_nm = esc_sql(r["fstvl_nm"])
        ctprvn_nm = esc_sql(r["ctprvn_nm"])
        signgu_nm = esc_sql(r["signgu_nm"])

        legaldong_nm = esc_sql(r.get("legaldong_nm", ""))
        adstrd_nm = esc_sql(r.get("adstrd_nm", ""))
        zip_no = esc_sql(r.get("zip_no", ""))
        addr1 = esc_sql(r.get("addr1", ""))
        tel_no = esc_sql(r.get("tel_no", ""))
        hmpg_addr = esc_sql(r.get("hmpg_addr", ""))

        mapx = num_or_null(r.get("mapx"))
        mapy = num_or_null(r.get("mapy"))

        first_image_url = esc_sql(r.get("first_image_url", ""))
        overview = esc_sql(r.get("overview", ""))

        # tourapi_content_id: bigint면 숫자로만 넣는 게 안전
        tourapi = r.get("tourapi_content_id")
        tourapi_lit = "NULL"
        if not is_nan(tourapi) and s(tourapi) != "":
            try:
                tourapi_lit = str(int(float(tourapi)))
            except Exception:
                tourapi_lit = "NULL"

        stmt = f"""
INSERT INTO festival_master
(fstvl_nm, ctprvn_nm, signgu_nm,
 legaldong_nm, adstrd_nm, zip_no, addr1,
 tel_no, hmpg_addr, mapx, mapy,
 first_image_url, overview, tourapi_content_id,
 detail_loaded, first_image_url2, original_image_url, image_urls)
SELECT
 '{fstvl_nm}', '{ctprvn_nm}', '{signgu_nm}',
 '{legaldong_nm}', '{adstrd_nm}', '{zip_no}', '{addr1}',
 '{tel_no}', '{hmpg_addr}', {mapx}, {mapy},
 '{first_image_url}', '{overview}', {tourapi_lit},
 b'0', NULL, NULL, NULL
WHERE NOT EXISTS (
  SELECT 1 FROM festival_master m
  WHERE m.fstvl_nm = '{fstvl_nm}'
    AND m.ctprvn_nm = '{ctprvn_nm}'
    AND m.signgu_nm = '{signgu_nm}'
  LIMIT 1
);
""".strip()

        lines.append(stmt)

    return lines


def build_event_insert_sql(df_all: pd.DataFrame) -> list[str]:
    """
    [현재 스키마 유지]
    festival_event 컬럼이 아래라고 가정:
    (master_id, raw_id, fclty_nm, fstvl_start, fstvl_end, origin_nm, data_base_de)

    - master_id는 festival_master에서 (fstvl_nm, ctprvn_nm, signgu_nm)로 조회해서 넣음
    - raw_id 중복은 NOT EXISTS로 차단
    """
    lines = []

    for _, r in df_all.iterrows():
        fstvl_nm = esc_sql(r["fstvl_nm"])
        ctprvn_nm = esc_sql(r["ctprvn_nm"])
        signgu_nm = esc_sql(r["signgu_nm"])

        raw_id = esc_sql(r["raw_id"])

        # 현재 스키마 유지: event 쪽 fclty_nm에도 축제명을 저장(중복이지만 컬럼 존재하니 유지)
        fclty_nm = esc_sql(r["fstvl_nm"])

        start_lit = date_literal(r.get("start_date"))
        end_lit = date_literal(r.get("end_date"))

        origin = esc_sql(r.get("origin_nm", "KC_488_WNTY_CLTFSTVL"))
        base_de = date_literal(r.get("data_base_de"))

        stmt = f"""
INSERT INTO festival_event
(master_id, raw_id, fclty_nm, fstvl_start, fstvl_end, origin_nm, data_base_de)
SELECT
    m.id,
    '{raw_id}',
    '{fclty_nm}',
    {start_lit},
    {end_lit},
    '{origin}',
    {base_de}
FROM festival_master m
WHERE m.fstvl_nm = '{fstvl_nm}'
  AND m.ctprvn_nm = '{ctprvn_nm}'
  AND m.signgu_nm = '{signgu_nm}'
  AND NOT EXISTS (SELECT 1 FROM festival_event e WHERE e.raw_id = '{raw_id}' LIMIT 1)
ORDER BY m.id ASC
LIMIT 1;
""".strip()

        lines.append(stmt)

    return lines


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", nargs="+", required=True, help="2019~2024 CSV 파일들 (여러 개 가능)")
    ap.add_argument("--out", required=True, help="생성될 SQL 파일 경로")
    args = ap.parse_args()

    in_paths = [Path(p) for p in args.input]
    out_path = Path(args.out)

    print("[INFO] inputs:")
    for p in in_paths:
        print(" -", p)

    dfs = []
    for p in in_paths:
        dfs.append(load_kc488_csv(p))

    df_all = pd.concat(dfs, ignore_index=True)

    # 빈 핵심키 제거(축제명/지역 없는 행은 제외)
    df_all = df_all[
        (df_all["fstvl_nm"].str.strip() != "") &
        (df_all["ctprvn_nm"].str.strip() != "") &
        (df_all["signgu_nm"].str.strip() != "")
    ].copy()

    df_master = df_all[[
        "fstvl_nm", "ctprvn_nm", "signgu_nm",
        "legaldong_nm", "adstrd_nm", "zip_no", "addr1",
        "tel_no", "hmpg_addr", "mapx", "mapy",
        "first_image_url", "overview", "tourapi_content_id",
    ]].copy()

    master_sql = build_master_insert_sql(df_master)
    event_sql = build_event_insert_sql(df_all)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("-- festival_master / festival_event import (2019~2024) [schema-keep version]\n")
        f.write("SET NAMES utf8mb4;\n")
        f.write("START TRANSACTION;\n\n")

        f.write("-- 1) master insert (skip if (fstvl_nm, ctprvn_nm, signgu_nm) exists)\n")
        for stmt in master_sql:
            f.write(stmt + "\n\n")

        f.write("-- 2) event insert (skip if raw_id exists)\n")
        for stmt in event_sql:
            f.write(stmt + "\n\n")

        f.write("COMMIT;\n")

    masters_cnt = df_master.drop_duplicates(subset=["fstvl_nm", "ctprvn_nm", "signgu_nm"]).shape[0]
    print(f"[INFO] 생성 완료: {out_path} (events={len(df_all)}, masters~={masters_cnt})")


if __name__ == "__main__":
    main()
