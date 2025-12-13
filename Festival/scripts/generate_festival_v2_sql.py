# Festival/scripts/generate_festival_v2_sql.py
from __future__ import annotations

import argparse
import math
from pathlib import Path
from typing import Optional, Tuple

import pandas as pd


def escape_sql(v) -> str:
    """SQL 문자열 안전 처리 (NULL/NaN/따옴표)"""
    if v is None:
        return ""
    if isinstance(v, float) and math.isnan(v):
        return ""
    s = str(v).strip()
    return s.replace("'", "''")


def to_date_literal(y, m, d) -> str:
    """
    y,m,d를 받아 DATE 리터럴로 변환.
    - y 또는 m 없으면 NULL
    - d 없으면 1일
    """
    if pd.isna(y) or pd.isna(m):
        return "NULL"
    yy = int(y)
    mm = int(m)
    dd = 1 if pd.isna(d) else int(d)
    return f"'{yy:04d}-{mm:02d}-{dd:02d}'"


def pick_col(df: pd.DataFrame, candidates: list[str]) -> Optional[str]:
    """후보 컬럼명 중 실제 존재하는 첫 컬럼 반환"""
    for c in candidates:
        if c in df.columns:
            return c
    return None


def load_excel_mcst_2025(path: Path) -> pd.DataFrame:
    """
    문체부 2025 '지역축제 개최계획 현황(0321)' 엑셀을 읽어 공통 포맷으로 정리.
    """
    # 보통 sheet_name="조사표", header=4 형태
    df_raw = pd.read_excel(path, sheet_name="조사표", header=4)
    df = df_raw[df_raw["연번"].notna()].copy()

    # 읍면동 컬럼이 케이스별로 다르게 잡히는 경우 대응
    eup_col = pick_col(df, ["읍면동", "Unnamed:10", "Unnamed: 10"])

    # 개최기간 컬럼들도 케이스별로 약간 달라질 수 있어서 후보로 잡아둠
    y1 = pick_col(df, ["개최기간", "개최기간(시작)", "시작(연)", "시작 연", "시작연", "Unnamed: 11", "Unnamed:11"])
    m1 = pick_col(df, ["Unnamed: 12", "Unnamed:12", "시작(월)", "시작 월", "시작월"])
    d1 = pick_col(df, ["Unnamed: 13", "Unnamed:13", "시작(일)", "시작 일", "시작일"])

    y2 = pick_col(df, ["Unnamed: 14", "Unnamed:14", "종료(연)", "종료 연", "종료연"])
    m2 = pick_col(df, ["Unnamed: 15", "Unnamed:15", "종료(월)", "종료 월", "종료월"])
    d2 = pick_col(df, ["Unnamed: 16", "Unnamed:16", "종료(일)", "종료 일", "종료일"])

    # 필수로 최소한 이건 있어야 함
    required = ["연번", "축제명", "광역자치단체명", "기초자치단체명"]
    missing = [c for c in required if c not in df.columns]
    if missing:
        raise ValueError(f"[ERROR] 엑셀에 필요한 컬럼이 없음: {missing}")

    if not (y1 and m1 and y2 and m2):
        raise ValueError(
            "[ERROR] 개최기간(시작/종료) 컬럼을 찾지 못했음. "
            "엑셀에서 시작/종료 연월 컬럼명이 바뀌었는지 확인 필요."
        )

    # 축제 개요 컬럼은 없을 수도 있음(없으면 빈값 처리)
    overview_col = pick_col(df, ["축제 개요", "축제개요", "개요", "행사 개요"])

    out = pd.DataFrame()
    out["seq"] = df["연번"].astype(int)

    out["fstvl_nm"] = df["축제명"].astype(str)
    out["ctprvn_nm"] = df["광역자치단체명"].astype(str)
    out["signgu_nm"] = df["기초자치단체명"].astype(str)

    # 분류(없으면 빈값)
    type_col = pick_col(df, ["축제 유형", "축제유형", "유형"])
    out["lclas_nm"] = df[type_col].astype(str) if type_col else ""
    out["mlsfc_nm"] = ""  # 일단 비워둠(나중에 확장 가능)

    # 장소
    place_col = pick_col(df, ["개최 장소", "개최장소", "장소"])
    out["legaldong_nm"] = df[place_col].astype(str) if place_col else ""
    out["adstrd_nm"] = df[eup_col].astype(str) if eup_col else ""

    # 날짜
    out["start_date"] = df.apply(lambda r: to_date_literal(r[y1], r[m1], r[d1] if d1 else None), axis=1)
    out["end_date"] = df.apply(lambda r: to_date_literal(r[y2], r[m2], r[d2] if d2 else None), axis=1)

    # 개요
    if overview_col:
        out["fstvl_cn"] = df[overview_col]
    else:
        out["fstvl_cn"] = ""

    return out


def build_sql(df: pd.DataFrame, include_ddl: bool) -> str:
    lines: list[str] = []

    if include_ddl:
        lines.append("-- ===== DDL =====")
        lines.append("SET FOREIGN_KEY_CHECKS = 0;")
        lines.append("DROP TABLE IF EXISTS festival_event;")
        lines.append("DROP TABLE IF EXISTS festival_master;")
        lines.append("SET FOREIGN_KEY_CHECKS = 1;")
        lines.append("")
        lines.append(
            """
CREATE TABLE festival_master (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  fstvl_nm     VARCHAR(255) NOT NULL,
  ctprvn_nm    VARCHAR(50)  NOT NULL,
  signgu_nm    VARCHAR(50)  NOT NULL,
  legaldong_nm VARCHAR(255) NULL,
  adstrd_nm    VARCHAR(255) NULL,
  zip_no       VARCHAR(20)  NULL,
  addr1        VARCHAR(255) NULL,
  tel_no       VARCHAR(50)  NULL,
  hmpg_addr    VARCHAR(500) NULL,
  mapx         DOUBLE NULL,
  mapy         DOUBLE NULL,
  first_image_url VARCHAR(1000) NULL,
  overview     TEXT NULL,
  tourapi_content_id BIGINT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_master (fstvl_nm, ctprvn_nm, signgu_nm),
  KEY idx_region (ctprvn_nm, signgu_nm),
  KEY idx_content (tourapi_content_id)
);
""".strip()
        )
        lines.append("")
        lines.append(
            """
CREATE TABLE festival_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  master_id BIGINT NOT NULL,
  raw_id     VARCHAR(64) NOT NULL,
  lclas_nm   VARCHAR(50) NULL,
  mlsfc_nm   VARCHAR(50) NULL,
  start_date DATE NOT NULL,
  end_date   DATE NOT NULL,
  fstvl_cn   TEXT NULL,
  origin_nm  VARCHAR(100) NULL,
  data_base_de DATE NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_event_master
    FOREIGN KEY (master_id) REFERENCES festival_master(id)
    ON DELETE CASCADE,
  KEY idx_period (start_date, end_date),
  KEY idx_raw (raw_id),
  KEY idx_master (master_id)
);
""".strip()
        )
        lines.append("")

    lines.append("-- ===== INSERTS =====")
    origin_nm = "문체부_지역축제계획_2025"

    for _, r in df.iterrows():
        seq = int(r["seq"])
        raw_id = f"MCST2025-{seq:04d}"

        fstvl_nm = escape_sql(r["fstvl_nm"])
        ctprvn_nm = escape_sql(r["ctprvn_nm"])
        signgu_nm = escape_sql(r["signgu_nm"])

        lclas_nm = escape_sql(r.get("lclas_nm", ""))
        mlsfc_nm = escape_sql(r.get("mlsfc_nm", ""))
        legaldong_nm = escape_sql(r.get("legaldong_nm", ""))
        adstrd_nm = escape_sql(r.get("adstrd_nm", ""))

        start_date = r["start_date"]
        end_date = r["end_date"]
        fstvl_cn = escape_sql(r.get("fstvl_cn", ""))

        if start_date == "NULL" or end_date == "NULL":
            # 날짜가 없는 레코드는 스킵 (필요하면 NULL 허용으로 바꿔도 됨)
            continue

        # 1) master upsert
        lines.append(
            "INSERT INTO festival_master (fstvl_nm, ctprvn_nm, signgu_nm, legaldong_nm, adstrd_nm)\n"
            f"VALUES ('{fstvl_nm}','{ctprvn_nm}','{signgu_nm}','{legaldong_nm}','{adstrd_nm}')\n"
            "ON DUPLICATE KEY UPDATE\n"
            "  legaldong_nm = VALUES(legaldong_nm),\n"
            "  adstrd_nm = VALUES(adstrd_nm);\n"
        )

        # 2) event insert (master_id는 subquery로 잡음)
        lines.append(
            "INSERT INTO festival_event (master_id, raw_id, lclas_nm, mlsfc_nm, start_date, end_date, fstvl_cn, origin_nm, data_base_de)\n"
            "VALUES (\n"
            f"  (SELECT id FROM festival_master WHERE fstvl_nm='{fstvl_nm}' AND ctprvn_nm='{ctprvn_nm}' AND signgu_nm='{signgu_nm}' LIMIT 1),\n"
            f"  '{raw_id}', '{lclas_nm}', '{mlsfc_nm}', {start_date}, {end_date}, '{fstvl_cn}', '{origin_nm}', NULL\n"
            ");\n"
        )

    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="엑셀(.xlsx) 또는 CSV 경로")
    ap.add_argument("--out", required=True, help="출력 SQL 파일 경로")
    ap.add_argument("--include-ddl", action="store_true", help="DDL 포함해서 생성")
    args = ap.parse_args()

    in_path = Path(args.input).resolve()
    out_path = Path(args.out).resolve()

    print(f"[INFO] input: {in_path}")
    print(f"[INFO] out  : {out_path}")

    suffix = in_path.suffix.lower()

    if suffix in (".xlsx", ".xls"):
        df = load_excel_mcst_2025(in_path)
    elif suffix == ".csv":
        # csv는 UTF-8 우선, 실패하면 CP949
        try:
            df = pd.read_csv(in_path, encoding="utf-8")
        except UnicodeDecodeError:
            df = pd.read_csv(in_path, encoding="cp949")
        # csv일 때는 컬럼명을 이 스크립트 포맷에 맞춰줘야 함(필요하면 여기서 매핑 추가)
        # 최소 컬럼: seq,fstvl_nm,ctprvn_nm,signgu_nm,start_date,end_date
        required = ["seq", "fstvl_nm", "ctprvn_nm", "signgu_nm", "start_date", "end_date"]
        missing = [c for c in required if c not in df.columns]
        if missing:
            raise ValueError(f"[ERROR] CSV에 필요한 컬럼이 없음: {missing}")
    else:
        raise ValueError("[ERROR] 지원하지 않는 입력 확장자 (xlsx/xls/csv만 가능)")

    sql_text = build_sql(df, include_ddl=args.include_ddl)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(sql_text, encoding="utf-8")

    print(f"[INFO] 생성 완료: {out_path} (rows: {len(df)})")


if __name__ == "__main__":
    main()
