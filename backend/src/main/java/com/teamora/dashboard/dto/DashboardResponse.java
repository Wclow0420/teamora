package com.teamora.dashboard.dto;

import java.util.List;

/** Admin Dashboard summary projection. Field names are contract — the app binds to them. */
public record DashboardResponse(
        long headcount,
        long presentToday,
        long onLeaveToday,
        long pendingApprovals,
        String payrollDueLabel,
        Week week,
        List<Activity> activity
) {

    /** Weekly attendance bar chart (Mon..Sun). */
    public record Week(
            List<String> labels,
            List<Integer> present,
            long max,
            String rateLabel
    ) {}

    /** A single recent-activity feed item. */
    public record Activity(
            String type,
            String text,
            String timeLabel,
            String accent
    ) {}
}
