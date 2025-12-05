from pathlib import Path
import math
import pandas as pd

# === 1. 파일 경로 설정 ===
# 프로젝트 루트 기준: data/raw/2025/...
BASE_DIR = Path(__file__).resolve().parents[1]
EXCEL_PATH = BASE_DIR / "data" / "raw" / "2025" / "2025년 지역축제 개최계획 현황(0321).xlsx"
OUT_SQL = BASE_DIR / "data" / "festival_2025_import.sql"


def build_date_literal(year, month, day):
    """
    연/월/일 숫자 3개를 받아서
    - 하나라도 없으면 NULL
    - 일자가 없으면 1일로 채워서 'YYYY-MM-01' 형태로 반환
    """
    if pd.isna(year) or pd.isna(month):
        return "NULL"

    y = int(year)
    m = int(month)
    if pd.isna(day):
        d = 1
    else:
        d = int(day)

    return f"'{y:04d}-{m:02d}-{d:02d}'"


def escape(s):
    """SQL에서 작은따옴표(') 깨지지 않도록 이스케이프"""
    if s is None:
        return ""
    if isinstance(s, float) and math.isnan(s):
        return ""
    return str(s).replace("'", "''").strip()


def main():
    print(f"[INFO] 엑셀 읽는 중: {EXCEL_PATH}")
    df_raw = pd.read_excel(EXCEL_PATH, sheet_name="조사표", header=4)

    # '연번'이 있는 실제 데이터 행만 사용
    df = df_raw[df_raw["연번"].notna()].copy()

    sql_lines = []
    for _, row in df.iterrows():
        # 기존 festival.raw_id와 겹치지 않게 접두어를 붙인다
        raw_id = f"MCST2025-{int(row['연번']):04d}"

        lclas_nm = escape(row.get("축제 유형"))
        mlsfc_nm = ""  # 세부 분류는 일단 비워둠

        fclty_nm = escape(row.get("축제명"))
        ctprvn_nm = escape(row.get("광역자치단체명"))
        signgu_nm = escape(row.get("기초자치단체명"))

        # 개최 장소 → 법정동 쪽 필드에 넣어두자
        legaldong_nm = escape(row.get("개최 장소"))

        # 읍면동 컬럼은 데이터프레임 상에서 'Unnamed:10' 이지만
        # header=4로 읽으면서 이름이 '읍면동'으로 잡혔을 수도 있고 아닐 수도 있다.
        # 안전하게 둘 다 체크
        if "읍면동" in df.columns:
            adstrd_nm = escape(row.get("읍면동"))
        elif "Unnamed:10" in df.columns:
            adstrd_nm = escape(row.get("Unnamed:10"))
        else:
            adstrd_nm = ""

        zip_no = ""

        # 좌표/전화/홈페이지는 이 파일에 없으니 비워둔다
        fclty_lo = None
        fclty_la = None
        tel_no = ""
        hmpg_addr = ""

        # 날짜: '개최기간', 'Unnamed: 12', 'Unnamed: 13', 'Unnamed: 14', 'Unnamed: 15', 'Unnamed: 16'
        fstvl_begin = build_date_literal(
            row.get("개최기간"),
            row.get("Unnamed: 12"),
            row.get("Unnamed: 13"),
        )
        fstvl_end = build_date_literal(
            row.get("Unnamed: 14"),
            row.get("Unnamed: 15"),
            row.get("Unnamed: 16"),
        )

        fstvl_cn = escape(row.get("축제 개요"))

        # data_base_de는 NULL로 두고, 출처만 남기기
        data_base_de = "NULL"
        origin_nm = "문체부_지역축제계획_2025"

        sql = (
            "INSERT INTO festival "
            "(raw_id,lclas_nm,mlsfc_nm,fclty_nm,ctprvn_nm,signgu_nm,legaldong_nm,adstrd_nm,"
            "zip_no,fclty_lo,fclty_la,fstvl_begin_de,fstvl_end_de,fstvl_cn,tel_no,hmpg_addr,"
            "data_base_de,origin_nm) VALUES ("
            f"'{raw_id}','{lclas_nm}','{mlsfc_nm}','{fclty_nm}',"
            f"'{ctprvn_nm}','{signgu_nm}','{legaldong_nm}','{adstrd_nm}',"
            f"'{zip_no}',NULL,NULL,{fstvl_begin},{fstvl_end},"
            f"'{fstvl_cn}','{tel_no}','{hmpg_addr}',{data_base_de},'{origin_nm}');"
        )

        sql_lines.append(sql)

    # SQL 파일로 저장
    OUT_SQL.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT_SQL, "w", encoding="utf-8") as f:
        f.write("-- 2025 지역축제 개최계획 데이터 INSERT 스크립트\n")
        for line in sql_lines:
            f.write(line + "\n")

    print(f"[INFO] 생성 완료: {OUT_SQL} (행 수: {len(sql_lines)})")


if __name__ == "__main__":
    main()
