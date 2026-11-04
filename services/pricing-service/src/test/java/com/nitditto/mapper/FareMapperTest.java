package com.nitditto.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.nitditto.model.Fare;
import com.nitditto.payload.request.FareRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FareMapperTest {

    private static FareRequest.FareRequestBuilder request() {
        return FareRequest.builder()
                .name("Economy Saver")
                .rbdCode('Y')
                .flightId(UUID.randomUUID())
                .cabinClassId(UUID.randomUUID())
                .baseFare(1_000.0);
    }

    @Test
    void toEntity_withoutCurrentPrice_defaultsAdjustmentToZero() {
        final Fare fare = FareMapper.toEntity(request().taxesAndFees(100.0).airlineFees(50.0).build());

        assertThat(fare.getCurrentPrice()).isEqualTo(0.0);
    }

    @Test
    void toEntity_withoutCurrentPrice_totalIsBasePlusTaxesPlusFeesOnly() {
        final Fare fare = FareMapper.toEntity(request().taxesAndFees(100.0).airlineFees(50.0).build());

        assertThat(fare.getTotalPrice()).isEqualTo(1_150.0);
    }

    @Test
    void toEntity_withNullTaxesAndFees_treatsThemAsZero() {
        final Fare fare = FareMapper.toEntity(request().build());

        assertThat(fare.getTotalPrice()).isEqualTo(1_000.0);
    }

    @Test
    void toEntity_withExplicitCurrentPrice_keepsItAsAdjustmentOnTopOfFare() {
        final Fare fare = FareMapper.toEntity(request().taxesAndFees(100.0).airlineFees(50.0).currentPrice(-200.0).build());

        assertThat(fare.getCurrentPrice()).isEqualTo(-200.0);
        assertThat(fare.getTotalPrice()).isEqualTo(950.0);
    }

    @Test
    void updateEntity_withoutCurrentPrice_leavesExistingAdjustmentUntouched() {
        final Fare fare = FareMapper.toEntity(request().currentPrice(-200.0).build());

        FareMapper.updateEntity(FareRequest.builder().baseFare(2_000.0).build(), fare);

        assertThat(fare.getCurrentPrice()).isEqualTo(-200.0);
        assertThat(fare.getTotalPrice()).isEqualTo(1_800.0);
    }
}
