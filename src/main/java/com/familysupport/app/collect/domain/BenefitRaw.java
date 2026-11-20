package com.familysupport.app.collect.domain;

import com.familysupport.app.benefit.domain.BenefitDraft;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 출처 원문 보관(사업당 최신본 1개) — Aggregate 루트(하위 엔티티 없음). 화면에는 보이지 않는다.
 * 원문이 바뀌었는지는 해시로 비교하고, 바뀐 때만 {@link #changedAt}을 갱신한다.
 * 사업(benefit)이 지워지면 DB의 FK(ON DELETE CASCADE)로 같이 지워진다.
 */
@Entity
@Table(name = "benefit_raw")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BenefitRaw {

    @Id
    private String benefitId;

    private String listFormat;
    private String listBody;
    private String conditionBody;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String bodyHash;

    private LocalDateTime changedAt;
    private LocalDateTime fetchedAt;

    public static BenefitRaw create(String benefitId, BenefitDraft.RawSource raw, LocalDateTime now) {
        BenefitRaw entity = new BenefitRaw();
        entity.benefitId = benefitId;
        entity.listFormat = raw.format();
        entity.listBody = raw.listBody();
        entity.conditionBody = raw.conditionBody();
        entity.bodyHash = hash(entity.listBody, entity.conditionBody);
        entity.changedAt = now;
        entity.fetchedAt = now;
        return entity;
    }

    /**
     * 다시 받은 원문으로 갱신한다. 정부24 조건을 이번에 부르지 않았으면(수정일이 같아 재사용) 지난 조건 원문을 그대로 둔다.
     */
    public void refresh(BenefitDraft.RawSource raw, LocalDateTime now) {
        String condition = raw.conditionBody() != null ? raw.conditionBody() : this.conditionBody;
        String newHash = hash(raw.listBody(), condition);
        if (!newHash.equals(bodyHash)) {
            this.listFormat = raw.format();
            this.listBody = raw.listBody();
            this.conditionBody = condition;
            this.bodyHash = newHash;
            this.changedAt = now;
        }
        this.fetchedAt = now;
    }

    static String hash(String listBody, String conditionBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(listBody.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            if (conditionBody != null) {
                digest.update(conditionBody.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }
}
