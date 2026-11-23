-- 우리 가족 지원 찾기 — MySQL 스키마 (family, family_test 공통)
-- 앱은 테이블을 만들지 않는다(ddl-auto=validate). 바뀌면 DBeaver에서 root로 직접 적용한다.
-- 계정·스키마 생성(1회):
--   CREATE DATABASE family      DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
--   CREATE DATABASE family_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
--   CREATE USER 'family'@'localhost' IDENTIFIED BY '<비밀번호>';   -- secret.properties FAMILY_DB_PASSWORD
--   GRANT SELECT, INSERT, UPDATE, DELETE ON family.*      TO 'family'@'localhost';
--   GRANT SELECT, INSERT, UPDATE, DELETE ON family_test.* TO 'family'@'localhost';

CREATE TABLE benefit (
    id              VARCHAR(80)   NOT NULL COMMENT '{출처}:{출처 사업ID} 예) GOV24:628000000781',
    source          VARCHAR(20)   NOT NULL COMMENT 'GOV24 / BOKJIRO_CENTRAL / BOKJIRO_LOCAL',
    source_id       VARCHAR(60)   NOT NULL COMMENT '출처의 사업 ID',
    name            VARCHAR(1000) NOT NULL COMMENT '사업명',
    organization    VARCHAR(1000)     NULL COMMENT '소관기관·담당부서',
    scope           VARCHAR(10)   NOT NULL COMMENT 'NATIONAL / SIDO / SIGUNGU',
    sido            VARCHAR(30)       NULL COMMENT '시·도 (전국이면 NULL)',
    sigungu         VARCHAR(50)       NULL COMMENT '시·군·구 (시·도 사업이면 NULL)',
    life_stages     VARCHAR(100)  NOT NULL COMMENT '대상 상황 PREPARING,PREGNANT,BIRTH,INFANT,NEWLYWED (콤마)',
    field_name      VARCHAR(1000)     NULL COMMENT '서비스분야·관심주제(원문)',
    summary         TEXT              NULL COMMENT '요약',
    target_text     MEDIUMTEXT        NULL COMMENT '지원대상(원문)',
    criteria_text   MEDIUMTEXT        NULL COMMENT '선정기준(원문) — 소득 기준선 등',
    support_text    MEDIUMTEXT        NULL COMMENT '지원내용(원문) — 금액 등',
    apply_method    MEDIUMTEXT        NULL COMMENT '신청방법',
    apply_period    TEXT              NULL COMMENT '신청기한(원문). 날짜 해석은 다음 버전',
    contact         TEXT              NULL COMMENT '문의처',
    household_types VARCHAR(1000)     NULL COMMENT '복지로 관련 가구유형(원문)',
    support_types   VARCHAR(200)      NULL COMMENT '지원 방식 CASH,DISCOUNT,VOUCHER,SERVICE,GOODS,LOAN,ETC (콤마)',
    fields          VARCHAR(200)      NULL COMMENT '분야 PREGNANCY,CHILDCARE,HEALTH,LIVING,HOUSING,WORK,EDU,ETC (콤마)',
    target_groups   VARCHAR(200)      NULL COMMENT '특정 대상 DISABLED,LOW_INCOME,MULTICULTURAL,SINGLE_PARENT,VETERAN,DEFECTOR (콤마, 사업명 기준)',
    detail_url      VARCHAR(2000)     NULL COMMENT '공식 안내 링크',
    source_modified VARCHAR(30)       NULL COMMENT '원문 수정일(출처 표기 그대로)',
    source_registered VARCHAR(30)     NULL COMMENT '원문 등록일(정부24 등록일시, 복지로 중앙 최초등록일) — NEW 표시',
    collected_at    DATETIME      NOT NULL COMMENT '우리가 마지막으로 받은 시각',
    content_changed_at DATETIME       NULL COMMENT '다시 받았을 때 원문 내용(문장·조건)이 실제로 바뀐 시각 — 변경 표시. 처음 저장 후 바뀐 적 없으면 NULL',
    PRIMARY KEY (id),
    CONSTRAINT ck_benefit_source CHECK (source IN ('GOV24', 'BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL')),
    CONSTRAINT ck_benefit_scope  CHECK (scope IN ('NATIONAL', 'SIDO', 'SIGUNGU')),
    CONSTRAINT ck_benefit_region CHECK (
        (scope = 'NATIONAL' AND sido IS NULL     AND sigungu IS NULL) OR
        (scope = 'SIDO'     AND sido IS NOT NULL AND sigungu IS NULL) OR
        (scope = 'SIGUNGU'  AND sido IS NOT NULL AND sigungu IS NOT NULL))
) ENGINE=InnoDB COMMENT='지원사업(출처별 1행 — 이름만으로 합치지 않는다)';

CREATE TABLE benefit_condition (
    benefit_id      VARCHAR(80)  NOT NULL COMMENT 'benefit.id',
    age_from        SMALLINT         NULL COMMENT '대상 나이 시작(JA0110)',
    age_to          SMALLINT         NULL COMMENT '대상 나이 끝(JA0111). 18 이하면 아이 나이로 본다',
    income_bands    CHAR(5)          NULL COMMENT '중위소득 5구간 허용 Y/N (50%↓,51~75,76~100,101~200,200%↑). 정보 없으면 NULL',
    flags           VARCHAR(1000)    NULL COMMENT '값이 Y인 조건 코드(나이·소득 제외, 콤마) 예) JA0302,JA0410,JA0412',
    fetched_at      DATETIME     NOT NULL COMMENT '조건을 받은 시각',
    PRIMARY KEY (benefit_id),
    CONSTRAINT fk_condition_benefit FOREIGN KEY (benefit_id) REFERENCES benefit (id) ON DELETE CASCADE,
    CONSTRAINT ck_condition_bands CHECK (income_bands IS NULL OR income_bands REGEXP '^[YN]{5}$'),
    CONSTRAINT ck_condition_age   CHECK (age_from IS NULL OR age_to IS NULL OR age_from <= age_to)
) ENGINE=InnoDB COMMENT='정부24 지원조건 — 화면에 그대로 보이지 않고 판정·이유 문장에만 쓴다';

CREATE TABLE benefit_raw (
    benefit_id      VARCHAR(80)  NOT NULL COMMENT 'benefit.id',
    list_format     VARCHAR(10)  NOT NULL COMMENT 'JSON(정부24) / XML(복지로)',
    list_body       MEDIUMTEXT   NOT NULL COMMENT '목록 응답의 이 사업 항목 원문 그대로',
    condition_body  TEXT             NULL COMMENT '정부24 지원조건 응답 원문(JSON)',
    body_hash       CHAR(64)     NOT NULL COMMENT 'list_body+condition_body의 SHA-256 — 바뀌었는지 빠르게 비교',
    changed_at      DATETIME     NOT NULL COMMENT '원문이 마지막으로 바뀐 시각(해시가 달라졌을 때만 갱신)',
    fetched_at      DATETIME     NOT NULL COMMENT '마지막으로 받은 시각',
    PRIMARY KEY (benefit_id),
    CONSTRAINT fk_raw_benefit FOREIGN KEY (benefit_id) REFERENCES benefit (id) ON DELETE CASCADE,
    CONSTRAINT ck_raw_format CHECK (list_format IN ('JSON', 'XML'))
) ENGINE=InnoDB COMMENT='출처 원문(최신본 1개) — 화면 노출 안 함';

CREATE TABLE collect_run (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    started_at    DATETIME      NOT NULL,
    finished_at   DATETIME          NULL,
    status        VARCHAR(10)   NOT NULL COMMENT 'RUNNING / SUCCESS / PARTIAL(일부 출처 실패) / FAILED',
    saved_count   INT           NOT NULL DEFAULT 0,
    removed_count INT           NOT NULL DEFAULT 0,
    message       VARCHAR(4000)     NULL COMMENT '출처별 건수·실패 사유(URL·인증키는 넣지 않음)',
    PRIMARY KEY (id),
    CONSTRAINT ck_run_status CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED'))
) ENGINE=InnoDB COMMENT='수집 실행 기록 — 화면에는 마지막 수집 시각만';
