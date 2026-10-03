package com.teamora.employee;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who sees NRIC / bank / salary / tax data: the directory list never carries it,
 * the detail endpoint only for OWNER/HR_ADMIN (MANAGER gets it nulled), and
 * company pay totals are OWNER/HR_ADMIN only.
 */
class PiiScopingIT extends AbstractIntegrationTest {

    private static final List<String> SENSITIVE = List.of(
            "nric", "epfNo", "socsoNo", "taxNo", "bankName", "bankAccountNo", "monthlySalary",
            "maritalStatus", "spouseWorking", "numChildren", "payBasis", "workingDays", "hoursPerDay",
            "derivedDailyRate", "derivedHourlyRate", "payBasisOverride", "workingDaysOverride", "hoursPerDayOverride");

    private record Co(String owner, String hr, String mgr, String empId, String inactiveId) {}

    private String createEmp(String token, String email, String role, String extra) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"%s\"%s}"
                .formatted(email, email, role, extra);
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(token))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private Co setup() throws Exception {
        String slug = "pii" + System.nanoTime();
        var reg = mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"companyName\":\"PII %s\",\"fullName\":\"Owner\",\"email\":\"%s-o@example.com\",\"password\":\"password\"}"
                                .formatted(slug, slug)))
                .andExpect(status().isOk()).andReturn();
        String owner = om.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();
        createEmp(owner, slug + "-hr@example.com", "HR_ADMIN", "");
        String mgrId = createEmp(owner, slug + "-mgr@example.com", "MANAGER", "");
        String empId = createEmp(owner, slug + "-emp@example.com", "EMPLOYEE",
                (",\"reportingManagerId\":\"%s\",\"monthlySalary\":4200,\"nric\":\"900101-14-5678\",\"epfNo\":\"EPF1\","
                        + "\"taxNo\":\"SG1\",\"bankName\":\"Maybank\",\"bankAccountNo\":\"5140 1234 5678\",\"numChildren\":2")
                        .formatted(mgrId));
        String inactiveId = createEmp(owner, slug + "-gone@example.com", "EMPLOYEE", ",\"monthlySalary\":3000");
        mvc.perform(post("/api/employees/" + inactiveId + "/deactivate").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        return new Co(owner, login(slug + "-hr@example.com", "password"), login(slug + "-mgr@example.com", "password"),
                empId, inactiveId);
    }

    private JsonNode body(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return om.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void list_isSlim_forEveryRole_andStillShowsInactivePeople() throws Exception {
        Co co = setup();
        for (String token : List.of(co.owner(), co.hr(), co.mgr())) {
            JsonNode list = body(mvc.perform(get("/api/employees").header("Authorization", bearer(token)))
                    .andExpect(status().isOk()));
            assertThat(list.size()).isEqualTo(5);
            for (JsonNode row : list) {
                for (String field : SENSITIVE) {
                    assertThat(row.has(field)).as("list row must not carry %s", field).isFalse();
                }
                assertThat(row.has("fullName")).isTrue();
                assertThat(row.has("active")).isTrue();
                assertThat(row.has("role")).isTrue();
            }
            JsonNode inactive = null;
            for (JsonNode row : list) {
                if (co.inactiveId().equals(row.get("id").asText())) inactive = row;
            }
            assertThat(inactive).as("deactivated employee still listed").isNotNull();
            assertThat(inactive.get("active").asBoolean()).isFalse();
        }
    }

    @Test
    void detail_hrAndOwnerSeeSensitiveFields_managerGetsThemNulled() throws Exception {
        Co co = setup();
        for (String token : List.of(co.owner(), co.hr())) {
            mvc.perform(get("/api/employees/" + co.empId()).header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nric").value("900101-14-5678"))
                    .andExpect(jsonPath("$.bankAccountNo").value("5140 1234 5678"))
                    .andExpect(jsonPath("$.monthlySalary").value(4200))
                    .andExpect(jsonPath("$.numChildren").value(2))
                    .andExpect(jsonPath("$.derivedDailyRate").isNotEmpty());
        }
        JsonNode asManager = body(mvc.perform(get("/api/employees/" + co.empId()).header("Authorization", bearer(co.mgr())))
                .andExpect(status().isOk()));
        for (String field : SENSITIVE) {
            assertThat(asManager.get(field).isNull()).as("manager view of %s", field).isTrue();
        }
        // …but the rest of the profile is there.
        assertThat(asManager.get("fullName").asText()).isNotBlank();
        assertThat(asManager.get("reportingManagerName").asText()).isNotBlank();
    }

    @Test
    void me_stillReturnsYourOwnSensitiveFields() throws Exception {
        // amir@lumi.com has a salary on file (seeded).
        String amir = login("amir@lumi.com", "password");
        mvc.perform(get("/api/employees/me").header("Authorization", bearer(amir)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlySalary").isNotEmpty());
    }

    @Test
    void payrollSummary_isOwnerAndHrOnly() throws Exception {
        String nadia = login("nadia@lumi.com", "password");
        mvc.perform(get("/api/admin/payroll/summary").header("Authorization", bearer(nadia)))
                .andExpect(status().isForbidden());
        String sarah = login("sarah@lumi.com", "password");
        // Lumi has seeded payslips → 200 with totals.
        mvc.perform(get("/api/admin/payroll/summary").header("Authorization", bearer(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netLabel").isNotEmpty());
        mvc.perform(get("/api/admin/payroll/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    void managerCannotChangeSensitiveFields() throws Exception {
        Co co = setup();
        mvc.perform(patch("/api/employees/" + co.empId()).header("Authorization", bearer(co.mgr()))
                        .contentType("application/json").content("{\"bankAccountNo\":\"999\"}"))
                .andExpect(status().isForbidden());
    }
}
