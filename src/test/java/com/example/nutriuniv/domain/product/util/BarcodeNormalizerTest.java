package com.example.nutriuniv.domain.product.util;

import com.example.nutriuniv.common.exception.CustomException;
import com.example.nutriuniv.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarcodeNormalizerTest {

    @Test
    void 열네자리는_그대로() {
        assertThat(BarcodeNormalizer.normalize("08809665551047")).isEqualTo("08809665551047");
    }

    @Test
    void 열세자리는_앞에_0을_채운다() {
        assertThat(BarcodeNormalizer.normalize("8809665551047")).isEqualTo("08809665551047");
    }

    @Test
    void 열두자리_UPC_A는_앞에_0을_채운다() {
        // UPC-A 036000291452 → 00036000291452
        assertThat(BarcodeNormalizer.normalize("036000291452")).isEqualTo("00036000291452");
    }

    @Test
    void 여덟자리_EAN_8은_앞에_0을_채운다() {
        // EAN-8 96385074 → 00000096385074
        assertThat(BarcodeNormalizer.normalize("96385074")).isEqualTo("00000096385074");
    }

    @Test
    void 앞뒤_공백은_무시한다() {
        assertThat(BarcodeNormalizer.normalize("  8809665551047 ")).isEqualTo("08809665551047");
    }

    @Test
    void 같은_상품은_몇_자리로_찍혀도_같은_값() {
        String from13 = BarcodeNormalizer.normalize("8801007965697");
        String from14 = BarcodeNormalizer.normalize("08801007965697");
        assertThat(from13).isEqualTo(from14);
    }

    @Test
    void 묶음_인디케이터가_있는_열네자리도_체크섬이_맞으면_받는다() {
        // 08809665551047 의 인디케이터를 1 로 바꾸고 체크 숫자를 다시 계산한 값
        String gtin = withCheckDigit("1880966555104");
        assertThat(BarcodeNormalizer.normalize(gtin)).isEqualTo(gtin);
    }

    @Test
    void 체크섬이_틀리면_BARCODE_CHECKSUM_INVALID() {
        assertThatThrownBy(() -> BarcodeNormalizer.normalize("08809665551048"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BARCODE_CHECKSUM_INVALID);
    }

    @Test
    void 지원하지_않는_길이는_BAD_REQUEST() {
        assertThatThrownBy(() -> BarcodeNormalizer.normalize("12345"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void 숫자가_아니면_BAD_REQUEST() {
        assertThatThrownBy(() -> BarcodeNormalizer.normalize("88096655510A7"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void 빈_값은_BAD_REQUEST() {
        assertThatThrownBy(() -> BarcodeNormalizer.normalize(" "))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void tryNormalize는_실패하면_빈_값() {
        assertThat(BarcodeNormalizer.tryNormalize("08809665551048")).isEmpty();
        assertThat(BarcodeNormalizer.tryNormalize("8809665551047")).contains("08809665551047");
    }

    @Test
    void isValidGtin14는_열네자리만_검사한다() {
        assertThat(BarcodeNormalizer.isValidGtin14("08809665551047")).isTrue();
        assertThat(BarcodeNormalizer.isValidGtin14("8809665551047")).isFalse();
        assertThat(BarcodeNormalizer.isValidGtin14(null)).isFalse();
    }

    private static String withCheckDigit(String first13) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int d = first13.charAt(i) - '0';
            sum += (i % 2 == 0) ? d * 3 : d;
        }
        return first13 + ((10 - sum % 10) % 10);
    }
}