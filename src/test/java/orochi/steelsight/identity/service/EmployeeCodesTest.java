package orochi.steelsight.identity.service;

import orochi.steelsight.exception.BusinessRuleException;
import orochi.steelsight.identity.repository.AppUserRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The Generate button's codes: role prefix, five digits, and a new draw whenever the code is taken. */
class EmployeeCodesTest {

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AccessPolicy access = mock(AccessPolicy.class);

    /** Hands out the given numbers in order, so a test decides which codes are drawn. */
    private static IntUnaryOperator draws(Integer... numbers) {
        Deque<Integer> queue = new ArrayDeque<>(List.of(numbers));
        return bound -> queue.isEmpty() ? 0 : queue.poll();
    }

    private void taken(String... codes) {
        Set<String> set = Set.of(codes);
        when(users.existsByEmployeeCodeIgnoreCase(anyString())).thenAnswer(call -> set.contains(call.getArgument(0, String.class)));
    }

    @Test
    void aWorkerGetsAWrkCodeWithFiveDigitsThatMeetsTheCodeRule() {
        taken();
        String code = new EmployeeCodes(users, access, draws(482)).suggest(1L, "WORKER");

        assertThat(code).isEqualTo("WRK-00482").matches("[A-Z0-9-]{3,20}");   // BR-06
        verify(access).require(1L, Permissions.USER_MANAGE);
    }

    @Test
    void eachRoleHasItsPrefixAndNoRoleGivesEmp() {
        taken();
        assertThat(new EmployeeCodes(users, access, draws(1)).suggest(1L, "MANAGER")).isEqualTo("MGR-00001");
        assertThat(new EmployeeCodes(users, access, draws(2)).suggest(1L, "HSE_OFFICER")).isEqualTo("HSE-00002");
        assertThat(new EmployeeCodes(users, access, draws(3)).suggest(1L, "ADMIN")).isEqualTo("ADM-00003");
        assertThat(new EmployeeCodes(users, access, draws(4)).suggest(1L, "")).isEqualTo("EMP-00004");
        assertThat(new EmployeeCodes(users, access, draws(5)).suggest(1L, null)).isEqualTo("EMP-00005");
    }

    @Test
    void aCodeSomeoneAlreadyHasIsDrawnAgainUntilAFreeOneComesUp() {
        taken("WRK-00007", "WRK-00008");
        String code = new EmployeeCodes(users, access, draws(7, 8, 9)).suggest(1L, "WORKER");

        assertThat(code).isEqualTo("WRK-00009");
        verify(users, times(3)).existsByEmployeeCodeIgnoreCase(anyString());
    }

    @Test
    void whenEveryDrawIsTakenItStopsAndAsksForACodeByHand() {
        when(users.existsByEmployeeCodeIgnoreCase(anyString())).thenReturn(true);

        assertThatThrownBy(() -> new EmployeeCodes(users, access, draws()).suggest(1L, "WORKER"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("No free employee code was found. Type one yourself.");
        verify(users, times(EmployeeCodes.MAX_TRIES)).existsByEmployeeCodeIgnoreCase(anyString());
    }
}
