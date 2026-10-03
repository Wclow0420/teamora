package com.teamora.config;

import com.teamora.auth.LoggingPasswordResetSender;
import com.teamora.auth.PasswordResetSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Refuses to start a {@code TEAMORA_ENV=production} instance that is configured
 * like a dev box. Runs while the context is being built, so a bad config never
 * serves a single request. Checks (production only):
 * <ul>
 *   <li>{@code TEAMORA_SEED} must be false — demo accounts share a public password;</li>
 *   <li>{@code PASSWORD_RESET_EXPOSE_CODE} must be false — it lets anyone reset any account;</li>
 *   <li>the logging password-reset sender (codes written to the server log) is only
 *       allowed when {@code PASSWORD_RESET_ENABLED=false}, in which case forgot-password
 *       answers 200 but creates and sends nothing.</li>
 * </ul>
 * {@code TEAMORA_ENV} itself must be {@code dev} or {@code production}. The JWT
 * secret is checked by {@link com.teamora.security.JwtService}.
 */
@Slf4j
@Component
public class ProductionSafetyCheck {

    public ProductionSafetyCheck(TeamoraProperties props, PasswordResetSender sender) {
        List<String> problems = problems(props, sender);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start: " + String.join(" ", problems));
        }
        if (props.isProduction()) {
            log.info("TEAMORA_ENV=production — startup safety checks passed");
        }
    }

    static List<String> problems(TeamoraProperties props, PasswordResetSender sender) {
        List<String> problems = new ArrayList<>();
        String env = props.env() == null ? TeamoraProperties.ENV_DEV : props.env().trim();
        if (!env.equalsIgnoreCase(TeamoraProperties.ENV_DEV) && !env.equalsIgnoreCase(TeamoraProperties.ENV_PRODUCTION)) {
            problems.add("TEAMORA_ENV must be 'dev' or 'production' (got '" + env + "').");
            return problems;
        }
        if (!props.isProduction()) {
            return problems;
        }
        if (props.seed()) {
            problems.add("TEAMORA_SEED=true in production (demo accounts use a public password) — set it to false.");
        }
        if (props.exposeResetCode()) {
            problems.add("PASSWORD_RESET_EXPOSE_CODE=true in production (anyone could reset any account) — set it to false.");
        }
        if (sender instanceof LoggingPasswordResetSender && props.passwordResetOrDefault().isEnabled()) {
            problems.add("Password reset is enabled but the only sender writes codes to the server log. "
                    + "Configure a real sender, or set PASSWORD_RESET_ENABLED=false until one exists.");
        }
        return problems;
    }
}
