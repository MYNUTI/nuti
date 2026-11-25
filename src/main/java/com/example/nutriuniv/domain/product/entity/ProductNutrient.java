package com.example.nutriuniv.domain.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_nutrients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductNutrient {

    @Id
    @Column(name = "product_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "serving_size", length = 50)
    private String servingSize;

    @Column(precision = 12, scale = 3)
    private BigDecimal calories;

    @Column(precision = 12, scale = 3)
    private BigDecimal carbohydrate;

    @Column(precision = 12, scale = 3)
    private BigDecimal sugar;

    @Column(precision = 12, scale = 3)
    private BigDecimal protein;

    @Column(precision = 12, scale = 3)
    private BigDecimal fat;

    @Column(name = "saturated_fat", precision = 12, scale = 3)
    private BigDecimal saturatedFat;

    @Column(name = "trans_fat", precision = 12, scale = 3)
    private BigDecimal transFat;

    @Column(precision = 12, scale = 3)
    private BigDecimal cholesterol;

    @Column(precision = 12, scale = 3)
    private BigDecimal sodium;

    @Column(name = "dietary_fiber", precision = 12, scale = 3)
    private BigDecimal fiber;

    @Column(name = "calories_per_100g", precision = 8, scale = 3)
    private BigDecimal caloriesPer100g;

    @Column(name = "carbohydrate_per_100g", precision = 8, scale = 3)
    private BigDecimal carbohydratePer100g;

    @Column(name = "sugar_per_100g", precision = 8, scale = 3)
    private BigDecimal sugarPer100g;

    @Column(name = "protein_per_100g", precision = 8, scale = 3)
    private BigDecimal proteinPer100g;

    @Column(name = "fat_per_100g", precision = 8, scale = 3)
    private BigDecimal fatPer100g;

    @Column(name = "saturated_fat_per_100g", precision = 8, scale = 3)
    private BigDecimal saturatedFatPer100g;

    @Column(name = "trans_fat_per_100g", precision = 8, scale = 3)
    private BigDecimal transFatPer100g;

    @Column(name = "cholesterol_per_100g", precision = 8, scale = 3)
    private BigDecimal cholesterolPer100g;

    @Column(name = "sodium_per_100g", precision = 8, scale = 3)
    private BigDecimal sodiumPer100g;

    @Column(name = "dietary_fiber_per_100g", precision = 8, scale = 3)
    private BigDecimal fiberPer100g;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static ProductNutrient create(Product product) {
        ProductNutrient n = new ProductNutrient();
        n.product = product;
        return n;
    }

    public void update(String servingSize,
                       BigDecimal calories, BigDecimal carbohydrate, BigDecimal sugar,
                       BigDecimal protein, BigDecimal fat, BigDecimal saturatedFat,
                       BigDecimal transFat, BigDecimal cholesterol, BigDecimal sodium,
                       BigDecimal fiber,
                       BigDecimal caloriesPer100g, BigDecimal carbohydratePer100g, BigDecimal sugarPer100g,
                       BigDecimal proteinPer100g, BigDecimal fatPer100g, BigDecimal saturatedFatPer100g,
                       BigDecimal transFatPer100g, BigDecimal cholesterolPer100g, BigDecimal sodiumPer100g,
                       BigDecimal fiberPer100g) {
        this.servingSize          = servingSize;
        this.calories             = calories;
        this.carbohydrate         = carbohydrate;
        this.sugar                = sugar;
        this.protein              = protein;
        this.fat                  = fat;
        this.saturatedFat         = saturatedFat;
        this.transFat             = transFat;
        this.cholesterol          = cholesterol;
        this.sodium               = sodium;
        this.fiber                = fiber;
        this.caloriesPer100g      = caloriesPer100g;
        this.carbohydratePer100g  = carbohydratePer100g;
        this.sugarPer100g         = sugarPer100g;
        this.proteinPer100g       = proteinPer100g;
        this.fatPer100g           = fatPer100g;
        this.saturatedFatPer100g  = saturatedFatPer100g;
        this.transFatPer100g      = transFatPer100g;
        this.cholesterolPer100g   = cholesterolPer100g;
        this.sodiumPer100g        = sodiumPer100g;
        this.fiberPer100g         = fiberPer100g;
    }
}