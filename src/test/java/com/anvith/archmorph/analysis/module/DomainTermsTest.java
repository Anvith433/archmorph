package com.anvith.archmorph.analysis.module;

import com.anvith.archmorph.analysis.module.naming.DomainTerms;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DomainTermsTest {

    @Test
    void stemsRemoveRolesAndVerbs() {
        assertThat(DomainTerms.stem("UserServiceImpl")).isEqualTo("user");
        assertThat(DomainTerms.stem("CreateOrderItemRequest")).isEqualTo("orderitem");
        assertThat(DomainTerms.stem("UserNotFoundException")).isEqualTo("user");
        assertThat(DomainTerms.stem("Categories")).isEqualTo("category");
        assertThat(DomainTerms.tokens("OrderItemRepository")).containsExactly("order", "item");
    }

    @Test
    void genericNamesCarryNoDomain() {
        assertThat(DomainTerms.isGenericName("ApiResponse")).isTrue();
        assertThat(DomainTerms.isGenericName("AppConstants")).isTrue();
        assertThat(DomainTerms.isGenericName("RequestProcessor")).isTrue();
        assertThat(DomainTerms.isGenericName("PaymentGateway")).isFalse();
    }

    @Test
    void pathTermsIgnoreVersionsAndVariables() {
        assertThat(DomainTerms.pathTerms("/api/v1/order-items/{id}")).containsExactly("orderitem");
    }

    @Test
    void tokenSimilarityRewardsSharedPrefixes() {
        assertThat(DomainTerms.tokenSimilarity(DomainTerms.tokens("Order"), DomainTerms.tokens("OrderItem"))).isGreaterThanOrEqualTo(0.6);
        assertThat(DomainTerms.tokenSimilarity(DomainTerms.tokens("User"), DomainTerms.tokens("Payment"))).isZero();
    }

    @Test
    void technologyPrefixesAndApiVersionsAreNotDomains() {
        assertThat(DomainTerms.stem("JdbcPetRepositoryImpl")).isEqualTo("pet");
        assertThat(DomainTerms.stem("SpringDataOwnerRepository")).isEqualTo("owner");
        assertThat(DomainTerms.stem("JpaVisitRepositoryImpl")).isEqualTo("visit");
        assertThat(DomainTerms.stem("JdbcPetRowMapper")).isEqualTo("pet");
        assertThat(DomainTerms.stem("PetTypeRepositoryOverride")).isEqualTo("pet");
        assertThat(DomainTerms.stem("OwnerRestControllerV2")).isEqualTo("owner");
        // a prefix only counts as a whole word
        assertThat(DomainTerms.stem("RestaurantService")).isEqualTo("restaurant");
        assertThat(DomainTerms.stem("MockingbirdController")).isEqualTo("mockingbird");
    }
}
