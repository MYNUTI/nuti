package com.example.nutriuniv.domain.product.service;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.domain.brand.entity.Brand;
import com.example.nutriuniv.domain.brand.repository.BrandRepository;
import com.example.nutriuniv.domain.category.entity.Category;
import com.example.nutriuniv.domain.product.entity.Product;
import com.example.nutriuniv.domain.product.entity.ProductCategoryChip;
import com.example.nutriuniv.domain.product.entity.ProductNutrient;
import com.example.nutriuniv.domain.product.entity.ProductStatus;
import com.example.nutriuniv.domain.product.repository.ProductCategoryChipRepository;
import com.example.nutriuniv.domain.product.repository.ProductNutrientRepository;
import com.example.nutriuniv.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 적재 한 제품 (v3 DATA_000) — master_id 갱신 · 해시 같으면 건너뜀 · 수동 수정 보호 · 바코드 13자리 · 분류 코드 · 칩. */
@ExtendWith(MockitoExtension.class)
class ProductCsvRowServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ProductNutrientRepository productNutrientRepository;
    @Mock ProductCategoryChipRepository chipRepository;
    @Mock BrandRepository brandRepository;
    @Mock ProductBarcodeService barcodeService;

    @InjectMocks ProductCsvRowService service;

    private Map<String, Category> categories;
    private Category tteok;      // 0505 떡·한과
    private Category porridge;   // 0210 죽·스프

    @BeforeEach
    void setUp() {
        Category snack = category("05", "과자·초콜릿·떡", null, 1L);
        Category meal = category("02", "간편식", null, 2L);
        tteok = category("0505", "떡·한과", snack, 10L);
        porridge = category("0210", "죽·스프", meal, 11L);
        categories = new HashMap<>();
        for (Category c : List.of(snack, meal, tteok, porridge)) categories.put(c.getCode(), c);
    }

    @Test
    void 새_제품은_만들고_바코드를_13자리로_넣고_성분과_상태를_채운다() {
        when(productRepository.findByMasterId("FQR-1")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));
        when(productNutrientRepository.findById(100L)).thenReturn(Optional.empty());

        ProductCsvRowService.Outcome out = service.apply(item(row("0505", "hash-1", "true"), "08809665551047"), categories, new HashMap<>(), false);

        assertEquals(ProductCsvRowService.Outcome.CREATED, out);
        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        Product p = saved.getValue();
        assertEquals("FQR-1", p.getMasterId());
        assertEquals("사천짜볶이", p.getName());
        assertSame(tteok, p.getCategory());
        assertNull(p.getBrand());                          // brand 빈칸 → 없음
        assertEquals("20020614179957", p.getReportNo());
        assertEquals("(주)아워홈", p.getMaker());
        assertTrue(p.isDiscontinued());
        assertEquals("hash-1", p.getContentHash());
        assertEquals(ProductStatus.ANALYZED, p.getStatus());
        verify(barcodeService).replace(p, List.of("8809665551047"));   // 14자리 → 앞 0 뗌

        ArgumentCaptor<ProductNutrient> n = ArgumentCaptor.forClass(ProductNutrient.class);
        verify(productNutrientRepository).save(n.capture());
        assertEquals(new BigDecimal("446.67"), n.getValue().getSodiumPer100g());
        assertNull(n.getValue().getFiberPer100g());        // 빈 칸은 결측
        verifyNoInteractions(chipRepository);              // 칩 열이 없으면 칩은 그대로
    }

    @Test
    void 해시가_같으면_건너뛴다() {
        Product existing = existing("hash-1", false);
        when(productRepository.findByMasterId("FQR-1")).thenReturn(Optional.of(existing));

        assertEquals(ProductCsvRowService.Outcome.UNCHANGED,
                service.apply(item(row("0505", "hash-1", "false"), "8809665551047"), categories, new HashMap<>(), false));
        verifyNoInteractions(barcodeService, productNutrientRepository);
    }

    @Test
    void 운영자가_고친_제품은_해시가_달라도_건너뛴다() {
        Product existing = existing("old", true);
        when(productRepository.findByMasterId("FQR-1")).thenReturn(Optional.of(existing));

        assertEquals(ProductCsvRowService.Outcome.PROTECTED,
                service.apply(item(row("0505", "new", "false"), "8809665551047"), categories, new HashMap<>(), false));
        assertEquals("old", existing.getContentHash());
        verifyNoInteractions(barcodeService, productNutrientRepository);
    }

    @Test
    void 해시가_다르면_값을_덮고_UPDATED() {
        Product existing = existing("old", false);
        when(productRepository.findByMasterId("FQR-1")).thenReturn(Optional.of(existing));
        when(productNutrientRepository.findById(100L)).thenReturn(Optional.empty());

        assertEquals(ProductCsvRowService.Outcome.UPDATED,
                service.apply(item(row("210", "new", "0"), "8809665551047"), categories, new HashMap<>(), false));
        assertSame(porridge, existing.getCategory());      // 엑셀이 지운 앞 0(210 → 0210)
        assertEquals("사천짜볶이", existing.getName());
        assertFalse(existing.isDiscontinued());
        verify(productRepository, never()).save(any());
    }

    @Test
    void 분류가_비면_분류_제외_바코드_오류는_그_바코드만_뺀다() {
        when(productRepository.findByMasterId("FQR-1")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));
        when(productNutrientRepository.findById(100L)).thenReturn(Optional.empty());

        service.apply(item(row("", "h", "false"), "8809665551047|8809665551040", "abc"), categories, new HashMap<>(), false);

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(saved.capture());
        assertNull(saved.getValue().getCategory());
        verify(barcodeService).replace(saved.getValue(), List.of("8809665551047"));   // 체크섬 오류·숫자 아님 제외
    }

    @Test
    void 없는_분류_코드나_대분류_코드면_실패() {
        when(productRepository.findByMasterId("FQR-1")).thenReturn(Optional.empty());
        assertThrows(CustomException.class,
                () -> service.apply(item(row("9999", "h", "false"), ""), categories, new HashMap<>(), false));
        assertThrows(CustomException.class,
                () -> service.apply(item(row("05", "h", "false"), ""), categories, new HashMap<>(), false));
        verify(productRepository, never()).save(any());
    }

    @Test
    void 생산_중단_값을_못_읽으면_실패() {
        when(productRepository.findByMasterId("FQR-1")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));
        assertThrows(CustomException.class,
                () -> service.apply(item(row("0505", "h", "maybe"), ""), categories, new HashMap<>(), false));
    }

    @Test
    void 브랜드는_캐시로_한_번만_찾는다() {
        when(productRepository.findByMasterId(any())).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));
        when(productNutrientRepository.findById(100L)).thenReturn(Optional.empty());
        Brand brand = Brand.create("아워홈");
        when(brandRepository.findByName("아워홈")).thenReturn(Optional.of(brand));
        Map<String, Brand> cache = new HashMap<>();

        Map<String, String> v = row("0505", "h", "false");
        v.put("brand", "아워홈");
        service.apply(item(v, ""), categories, cache, false);
        service.apply(item(v, ""), categories, cache, false);

        verify(brandRepository, times(1)).findByName("아워홈");
    }

    @Test
    void 같이_보이는_칩은_소속_중분류를_빼고_최대_2개() {
        when(productRepository.findByMasterId("FQR-1")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));
        when(productNutrientRepository.findById(100L)).thenReturn(Optional.empty());
        when(chipRepository.findByProductId(100L)).thenReturn(List.of());

        Map<String, String> v = row("0505", "h", "false");
        v.put("extra_categories", "0505|0210");
        service.apply(item(v, ""), categories, new HashMap<>(), true);

        ArgumentCaptor<ProductCategoryChip> chip = ArgumentCaptor.forClass(ProductCategoryChip.class);
        verify(chipRepository, times(1)).save(chip.capture());
        assertSame(porridge, chip.getValue().getCategory());
    }

    @Test
    void 칩이_3개면_실패() {
        when(productRepository.findByMasterId("FQR-1")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> withId(inv.getArgument(0), 100L));
        categories.put("0201", category("0201", "국·탕·찌개", categories.get("02"), 12L));
        categories.put("0501", category("0501", "스낵", categories.get("05"), 13L));

        Map<String, String> v = row("0505", "h", "false");
        v.put("extra_categories", "0210|0201|0501");
        assertThrows(CustomException.class, () -> service.apply(item(v, ""), categories, new HashMap<>(), true));
    }

    @Test
    void 값_읽기_규칙() {
        assertEquals("0505", ProductCsvRowService.code("505"));
        assertEquals("0505", ProductCsvRowService.code("0505"));
        assertTrue(ProductCsvRowService.parseBoolean("TRUE"));
        assertFalse(ProductCsvRowService.parseBoolean(""));
        assertNull(ProductCsvRowService.parseNutrient(""));
        assertNull(ProductCsvRowService.parseNutrient("-"));
        assertEquals(new BigDecimal("0.07"), ProductCsvRowService.parseNutrient("0.07"));
    }

    // ── 도우미 ──

    /** 데이터 팀 예시 첫 행(사천짜볶이) 값. */
    private static Map<String, String> row(String category, String hash, String discontinued) {
        Map<String, String> v = new HashMap<>();
        v.put("master_id", "FQR-1");
        v.put("report_no", "20020614179957");
        v.put("source", "foodqr");
        v.put("product_name", "사천짜볶이");
        v.put("brand", "");
        v.put("category", category);
        v.put("maker", "(주)아워홈");
        v.put("ingredients", "");
        v.put("serving_size", "300g");
        String[] nutrients = {"calories_kcal", "sodium_mg", "carbohydrate_g", "sugar_g", "fat_g", "trans_fat_g",
                "saturated_fat_g", "cholesterol_mg", "dietary_fiber_g", "protein_g"};
        String[] base = {"600", "1340", "128", "20", "4.2", "0.2", "0.6", "0", "", "12"};
        String[] per100 = {"200", "446.67", "42.67", "6.67", "1.4", "0.07", "0.2", "0", "", "4"};
        for (int i = 0; i < nutrients.length; i++) {
            v.put(nutrients[i], base[i]);
            v.put(nutrients[i] + "_per_100g", per100[i]);
        }
        v.put("is_discontinued", discontinued);
        v.put("content_hash", hash);
        return v;
    }

    private static ProductCsvRowService.LoadItem item(Map<String, String> values, String... barcodes) {
        return new ProductCsvRowService.LoadItem(2, values.get("master_id"), values, new ArrayList<>(List.of(barcodes)));
    }

    private static Product existing(String hash, boolean corrected) {
        Product p = withId(Product.loaded("FQR-1", "옛 이름"), 100L);
        p.applyLoad("옛 이름", null, null, null, null, null, null, true, hash);
        if (corrected) p.markManuallyCorrected();
        return p;
    }

    private static Product withId(Product p, long id) {
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static Category category(String code, String name, Category parent, long id) {
        Category c = Category.seeded(code, name, parent, 1);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
}
