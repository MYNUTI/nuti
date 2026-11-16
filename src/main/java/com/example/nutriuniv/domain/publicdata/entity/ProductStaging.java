package com.example.nutriuniv.domain.publicdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 제품 복제 테이블 (공공데이터 최신화) — 데이터 팀 34컬럼 최종 테이블과 같은 모양. 실제 product 에 반영하기 전 대기 장소.
 * <ul>
 *   <li>컬럼 이름 = 적재 CSV 헤더 이름. 한 행을 그대로 「컬럼명 → 값」 맵으로 읽어 ProductCsvRowService 에 넘긴다</li>
 *   <li>값은 전부 hash 에 쓰는 문자열 그대로 저장한다(숫자도 "446.67"). 다시 숫자로 바꿨다가 문자열로 만들면 표기가 달라질 수 있어서다</li>
 *   <li>빈 값은 NULL. content_hash 는 ContentHashCalculator 로 이 행의 30칸에서 계산한 값</li>
 *   <li>created_at · updated_at 은 두지 않는다 — 실제 product 의 시각은 서버가 반영할 때 기록한다</li>
 * </ul>
 * 읽기·쓰기는 ProductStagingRepository(JDBC)가 한다. 이 엔티티는 표를 만들기(ddl-auto) 위한 정의다.
 */
@Entity
@Table(name = "product_staging")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductStaging {

    @Id
    @Column(name = "master_id", length = 40)
    private String masterId;

    @Column(length = 14)
    private String barcode;

    @Column(name = "report_no", length = 20)
    private String reportNo;

    @Column(length = 30)
    private String source;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(length = 255)
    private String brand;

    @Column(length = 10)
    private String category;

    @Column(length = 255)
    private String maker;

    @Column(columnDefinition = "text")
    private String ingredients;

    @Column(name = "serving_size", length = 50)
    private String servingSize;

    // ── 영양성분 10종 (한 봉지 전체 기준) ──
    @Column(name = "calories_kcal", length = 30)   private String caloriesKcal;
    @Column(name = "sodium_mg", length = 30)       private String sodiumMg;
    @Column(name = "carbohydrate_g", length = 30)  private String carbohydrateG;
    @Column(name = "sugar_g", length = 30)         private String sugarG;
    @Column(name = "fat_g", length = 30)           private String fatG;
    @Column(name = "trans_fat_g", length = 30)     private String transFatG;
    @Column(name = "saturated_fat_g", length = 30) private String saturatedFatG;
    @Column(name = "cholesterol_mg", length = 30)  private String cholesterolMg;
    @Column(name = "dietary_fiber_g", length = 30) private String dietaryFiberG;
    @Column(name = "protein_g", length = 30)       private String proteinG;

    // ── 영양성분 10종 100g 환산값 ──
    @Column(name = "calories_kcal_per_100g", length = 30)   private String caloriesKcalPer100g;
    @Column(name = "sodium_mg_per_100g", length = 30)       private String sodiumMgPer100g;
    @Column(name = "carbohydrate_g_per_100g", length = 30)  private String carbohydrateGPer100g;
    @Column(name = "sugar_g_per_100g", length = 30)         private String sugarGPer100g;
    @Column(name = "fat_g_per_100g", length = 30)           private String fatGPer100g;
    @Column(name = "trans_fat_g_per_100g", length = 30)     private String transFatGPer100g;
    @Column(name = "saturated_fat_g_per_100g", length = 30) private String saturatedFatGPer100g;
    @Column(name = "cholesterol_mg_per_100g", length = 30)  private String cholesterolMgPer100g;
    @Column(name = "dietary_fiber_g_per_100g", length = 30) private String dietaryFiberGPer100g;
    @Column(name = "protein_g_per_100g", length = 30)       private String proteinGPer100g;

    @Column(name = "is_discontinued", length = 5)
    private String isDiscontinued;

    @Column(name = "content_hash", length = 64, nullable = false)
    private String contentHash;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;
}