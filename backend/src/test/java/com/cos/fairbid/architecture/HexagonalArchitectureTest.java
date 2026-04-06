package com.cos.fairbid.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 헥사고날 아키텍처 가드레일 테스트.
 * 이 테스트가 실패하면 아키텍처 규칙을 위반한 것이다.
 * ./gradlew test 로 실행되며, CI에서도 자동으로 돌아간다.
 */
class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.cos.fairbid");
    }

    @Nested
    @DisplayName("Domain 레이어 규칙")
    class DomainLayerRules {

        @Test
        @DisplayName("Domain은 JPA(jakarta.persistence)에 의존하면 안 된다")
        void domain_should_not_depend_on_jpa() {
            noClasses()
                    .that().resideInAnyPackage("..domain..", "..domain.policy..", "..domain.event..", "..domain.exception..")
                    .and().resideOutsideOfPackage("..adapter..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "jakarta.persistence..",
                            "javax.persistence.."
                    )
                    .because("Domain은 순수 POJO여야 한다. JPA 어노테이션은 Entity(adapter/out/persistence)에서만 사용한다.")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain은 Spring Framework에 의존하면 안 된다 (HttpStatus 제외)")
        void domain_should_not_depend_on_spring() {
            noClasses()
                    .that().resideInAnyPackage("..domain..", "..domain.policy..", "..domain.event..", "..domain.exception..")
                    .and().resideOutsideOfPackage("..adapter..")
                    .should().dependOnClassesThat(
                            com.tngtech.archunit.base.DescribedPredicate.describe(
                                    "Spring classes (HttpStatus 제외)",
                                    clazz -> clazz.getPackageName().startsWith("org.springframework")
                                            && !clazz.getName().equals("org.springframework.http.HttpStatus")
                            )
                    )
                    .because("Domain은 순수 POJO여야 한다. Spring 어노테이션을 사용하면 안 된다. (DomainException의 HttpStatus는 기존 설계로 허용)")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain은 Adapter 레이어에 의존하면 안 된다")
        void domain_should_not_depend_on_adapter() {
            noClasses()
                    .that().resideInAnyPackage("..domain..", "..domain.policy..", "..domain.event..", "..domain.exception..")
                    .and().resideOutsideOfPackage("..adapter..")
                    .should().dependOnClassesThat().resideInAnyPackage("..adapter..")
                    .because("Domain → Adapter 의존은 헥사고날 아키텍처 위반이다.")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Controller 레이어 규칙")
    class ControllerLayerRules {

        @Test
        @DisplayName("Controller는 Repository Port에 직접 의존하면 안 된다")
        void controller_should_not_depend_on_repository_port() {
            noClasses()
                    .that().resideInAnyPackage("..adapter.in.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage("..application.port.out..")
                    .because("Controller는 UseCase(Port In)를 통해서만 비즈니스 로직에 접근해야 한다.")
                    .check(classes);
        }

        @Test
        @DisplayName("Controller는 Service 구현체에 직접 의존하면 안 된다")
        void controller_should_not_depend_on_service_impl() {
            noClasses()
                    .that().resideInAnyPackage("..adapter.in.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage("..application.service..")
                    .because("Controller는 UseCase 인터페이스(Port In)에만 의존해야 한다. Service 구현체에 직접 의존하면 안 된다.")
                    .check(classes);
        }

        @Test
        @DisplayName("Controller는 Entity에 의존하면 안 된다")
        void controller_should_not_depend_on_entity() {
            noClasses()
                    .that().resideInAnyPackage("..adapter.in.controller..")
                    .should().dependOnClassesThat().resideInAnyPackage("..adapter.out.persistence..")
                    .because("Controller는 Entity를 직접 사용하면 안 된다. Response DTO로 변환해야 한다.")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Service 레이어 규칙")
    class ServiceLayerRules {

        @Test
        @DisplayName("Service는 Entity에 직접 의존하면 안 된다")
        void service_should_not_depend_on_entity() {
            noClasses()
                    .that().resideInAnyPackage("..application.service..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..adapter.out.persistence.entity..",
                            "..adapter.out.persistence.repository.."
                    )
                    .because("Service는 Entity를 직접 다루면 안 된다. Port Out 인터페이스를 통해 Domain 객체로 변환된 결과만 사용한다.")
                    .check(classes);
        }

        @Test
        @DisplayName("Service는 Controller에 의존하면 안 된다")
        void service_should_not_depend_on_controller() {
            noClasses()
                    .that().resideInAnyPackage("..application.service..")
                    .should().dependOnClassesThat().resideInAnyPackage("..adapter.in.controller..")
                    .because("Service → Controller 의존은 헥사고날 아키텍처 위반이다.")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Port 레이어 규칙")
    class PortLayerRules {

        @Test
        @DisplayName("Port In(UseCase)은 인터페이스여야 한다 (inner record 제외)")
        void port_in_should_be_interfaces() {
            classes()
                    .that().resideInAnyPackage("..application.port.in..")
                    .and().areNotMemberClasses() // UseCase 내부 record (Command, Result 등) 제외
                    .should().beInterfaces()
                    .because("UseCase는 인터페이스로 정의하고, Service가 구현해야 한다. 내부 Command/Result record는 허용.")
                    .check(classes);
        }

        @Test
        @DisplayName("Port Out은 인터페이스여야 한다 (record/inner class 제외)")
        void port_out_should_be_interfaces() {
            classes()
                    .that().resideInAnyPackage("..application.port.out..")
                    .and().areNotMemberClasses() // Port Out 내부 record 제외
                    .and().areNotRecords() // top-level record (OAuthUserInfo 등 DTO) 제외
                    .should().beInterfaces()
                    .because("Port Out은 인터페이스로 정의하고, Adapter가 구현해야 한다. DTO record는 허용.")
                    .check(classes);
        }
    }
}
