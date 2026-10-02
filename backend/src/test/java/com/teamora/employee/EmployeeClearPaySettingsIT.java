package com.teamora.employee;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH /api/employees/{id} clear flags for pay settings: clearMonthlySalary /
 * clearWorkingDays / clearHoursPerDay / clearPayBasis null the employee's override
 * so they fall back to the company default (the response reports the EFFECTIVE
 * schedule, so the stored column is checked through the repository).
 */
class EmployeeClearPaySettingsIT extends AbstractIntegrationTest {

    /** Overrides that all differ from a new company's defaults (MONTHLY, Mon–Fri = 31, 8h). */
    private static final String OVERRIDES =
            "{\"monthlySalary\":4000.00,\"payBasis\":\"DAILY\",\"workingDays\":63,\"hoursPerDay\":6.5}";

    @Autowired
    private EmployeeRepository employees;

    private record Co(String ownerToken, String employeeToken, String employeeId) {}

    private Co company(String tag) throws Exception {
        String slug = tag + System.nanoTime();
        var reg = mvc.perform(post("/api/auth/register").contentType("application/json").content("""
                        {"companyName":"CP %s","fullName":"Owner %s","email":"cp-owner-%s@example.com","password":"password"}"""
                        .formatted(slug, slug, slug)))
                .andExpect(status().isOk()).andReturn();
        String owner = om.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();
        String email = "cp-emp-" + slug + "@example.com";
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"fullName\":\"Emp %s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"EMPLOYEE\"}"
                                .formatted(slug, email)))
                .andExpect(status().isCreated()).andReturn();
        String id = om.readTree(res.getResponse().getContentAsString()).get("id").asText();
        Co co = new Co(owner, login(email, "password"), id);
        // Start every scenario with all four overrides set.
        patchEmp(co, OVERRIDES).andExpect(status().isOk())
                .andExpect(jsonPath("$.payBasis").value("DAILY"))
                .andExpect(jsonPath("$.workingDays").value(63))
                .andExpect(jsonPath("$.hoursPerDay").value(6.5));
        return co;
    }

    private ResultActions patchEmp(Co co, String json) throws Exception {
        return mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                .contentType("application/json").content(json));
    }

    private Employee stored(Co co) {
        return employees.findById(UUID.fromString(co.employeeId())).orElseThrow();
    }

    /** The other three overrides are untouched. */
    private void assertOthersKept(Employee e, String cleared) {
        if (!cleared.equals("salary")) assertEquals(0, new BigDecimal("4000").compareTo(e.getMonthlySalary()));
        if (!cleared.equals("basis")) assertEquals(PayBasis.DAILY, e.getPayBasis());
        if (!cleared.equals("days")) assertEquals((short) 63, e.getWorkingDays());
        if (!cleared.equals("hours")) assertEquals(0, new BigDecimal("6.5").compareTo(e.getHoursPerDay()));
    }

    @Test
    void clearMonthlySalary_nullsIt() throws Exception {
        Co co = company("sal");
        patchEmp(co, "{\"clearMonthlySalary\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlySalary").value(nullValue()));
        assertNull(stored(co).getMonthlySalary());
        assertOthersKept(stored(co), "salary");
    }

    @Test
    void clearPayBasis_fallsBackToCompanyDefault() throws Exception {
        Co co = company("bas");
        patchEmp(co, "{\"clearPayBasis\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.payBasis").value("MONTHLY"))
                .andExpect(jsonPath("$.payBasisOverride").value(nullValue()))
                // The untouched overrides are still reported as personal values.
                .andExpect(jsonPath("$.workingDaysOverride").value(63))
                .andExpect(jsonPath("$.hoursPerDayOverride").value(6.5));
        assertNull(stored(co).getPayBasis());
        assertOthersKept(stored(co), "basis");
    }

    @Test
    void clearWorkingDays_fallsBackToCompanyDefault() throws Exception {
        Co co = company("day");
        patchEmp(co, "{\"clearWorkingDays\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.workingDays").value(31))
                .andExpect(jsonPath("$.workingDaysOverride").value(nullValue()))
                .andExpect(jsonPath("$.payBasisOverride").value("DAILY"));
        assertNull(stored(co).getWorkingDays());
        assertOthersKept(stored(co), "days");
    }

    @Test
    void clearHoursPerDay_fallsBackToCompanyDefault() throws Exception {
        Co co = company("hrs");
        JsonNode res = om.readTree(patchEmp(co, "{\"clearHoursPerDay\":true}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, new BigDecimal("8").compareTo(res.get("hoursPerDay").decimalValue()));
        assertTrue(res.has("hoursPerDayOverride") && res.get("hoursPerDayOverride").isNull());
        assertNull(stored(co).getHoursPerDay());
        assertOthersKept(stored(co), "hours");
    }

    @Test
    void allFourAtOnce_alongsideAnotherField() throws Exception {
        Co co = company("all");
        patchEmp(co, "{\"jobTitle\":\"Barista\",\"clearMonthlySalary\":true,\"clearPayBasis\":true,"
                + "\"clearWorkingDays\":true,\"clearHoursPerDay\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Barista"))
                .andExpect(jsonPath("$.monthlySalary").value(nullValue()))
                .andExpect(jsonPath("$.payBasis").value("MONTHLY"))
                .andExpect(jsonPath("$.workingDays").value(31))
                .andExpect(jsonPath("$.payBasisOverride").value(nullValue()))
                .andExpect(jsonPath("$.workingDaysOverride").value(nullValue()))
                .andExpect(jsonPath("$.hoursPerDayOverride").value(nullValue()));
        // Setting them again reports the personal values, on PATCH and on the detail GET.
        patchEmp(co, "{\"payBasis\":\"HOURLY\",\"workingDays\":7,\"hoursPerDay\":4.5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payBasisOverride").value("HOURLY"))
                .andExpect(jsonPath("$.workingDaysOverride").value(7))
                .andExpect(jsonPath("$.hoursPerDayOverride").value(4.5));
        mvc.perform(get("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payBasis").value("HOURLY"))
                .andExpect(jsonPath("$.payBasisOverride").value("HOURLY"))
                .andExpect(jsonPath("$.workingDaysOverride").value(7))
                .andExpect(jsonPath("$.hoursPerDayOverride").value(4.5));
        patchEmp(co, "{\"clearPayBasis\":true,\"clearWorkingDays\":true,\"clearHoursPerDay\":true}")
                .andExpect(status().isOk());
        Employee e = stored(co);
        assertNull(e.getMonthlySalary());
        assertNull(e.getPayBasis());
        assertNull(e.getWorkingDays());
        assertNull(e.getHoursPerDay());
    }

    @Test
    void falseOrOmittedFlags_leaveOverridesAlone() throws Exception {
        Co co = company("keep");
        patchEmp(co, "{\"jobTitle\":\"Cashier\",\"clearMonthlySalary\":false,\"clearPayBasis\":false,"
                + "\"clearWorkingDays\":false,\"clearHoursPerDay\":false}")
                .andExpect(status().isOk());
        Employee e = stored(co);
        assertNotNull(e.getMonthlySalary());
        assertOthersKept(e, "");
        // A false flag next to a value is not a conflict.
        patchEmp(co, "{\"monthlySalary\":4500,\"clearMonthlySalary\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlySalary").value(4500));
    }

    @Test
    void valueAndItsClearFlagTogether_400_andNothingChanges() throws Exception {
        Co co = company("both");
        patchEmp(co, "{\"monthlySalary\":5000,\"clearMonthlySalary\":true}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Send either monthlySalary or clearMonthlySalary, not both"));
        patchEmp(co, "{\"payBasis\":\"HOURLY\",\"clearPayBasis\":true}").andExpect(status().isBadRequest());
        patchEmp(co, "{\"workingDays\":31,\"clearWorkingDays\":true}").andExpect(status().isBadRequest());
        // The rest of a conflicting request is not applied either.
        patchEmp(co, "{\"jobTitle\":\"Nope\",\"hoursPerDay\":8,\"clearHoursPerDay\":true}").andExpect(status().isBadRequest());

        Employee e = stored(co);
        assertOthersKept(e, "");
        assertNull(e.getJobTitle());
    }

    @Test
    void permissions_401_and_403() throws Exception {
        Co co = company("perm");
        mvc.perform(patch("/api/employees/" + co.employeeId()).contentType("application/json")
                        .content("{\"clearMonthlySalary\":true}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.employeeToken()))
                        .contentType("application/json").content("{\"clearMonthlySalary\":true}"))
                .andExpect(status().isForbidden());
        assertNotNull(stored(co).getMonthlySalary());
    }
}
