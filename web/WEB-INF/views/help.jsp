<%--
    Help, and the clinic's own settings.

    The settings table is read from the database rather than written into this page, so it cannot
    describe a rule the application is not actually applying - which is what usually happens to
    hand-written documentation about configurable values.

    The permissions list is generated from the same Role predicates the navigation menu and the
    facade ask, so "why can I not see the revenue report" is answered here rather than by reading
    the source.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Help and clinic settings"
        subtitle="How the system behaves, and what your role permits." wide="true">

    <div class="two-column">

        <section class="panel-card">
            <header class="card-head"><h2>What your role permits</h2></header>

            <p>You are signed in as <strong><c:out value="${sessionUser.fullName}"/></strong>
                (<c:out value="${role.label}"/>).</p>

            <ul class="permission-list">
                <li class="${role.canRegisterAppointments() ? 'yes' : 'no'}">
                    <span aria-hidden="true">${role.canRegisterAppointments() ? '✓' : '✕'}</span>
                    Register patients and appointments
                </li>
                <li class="${role.canUpdateAppointmentStatus() ? 'yes' : 'no'}">
                    <span aria-hidden="true">${role.canUpdateAppointmentStatus() ? '✓' : '✕'}</span>
                    Record whether a patient attended
                </li>
                <li class="${role.canGenerateBills() ? 'yes' : 'no'}">
                    <span aria-hidden="true">${role.canGenerateBills() ? '✓' : '✕'}</span>
                    Price visits and issue bills
                </li>
                <li class="${role.canViewOperationalReports() ? 'yes' : 'no'}">
                    <span aria-hidden="true">${role.canViewOperationalReports() ? '✓' : '✕'}</span>
                    View the dashboard and operational reports
                </li>
                <li class="${role.canViewFinancialReports() ? 'yes' : 'no'}">
                    <span aria-hidden="true">${role.canViewFinancialReports() ? '✓' : '✕'}</span>
                    View revenue and billing-summary reports
                </li>
                <li class="${role.canManageMasterData() ? 'yes' : 'no'}">
                    <span aria-hidden="true">${role.canManageMasterData() ? '✓' : '✕'}</span>
                    Manage staff accounts and dentists
                </li>
            </ul>

            <p class="fineprint">
                Menu items you may not use are not shown at all, and the URLs behind them are
                refused as well &mdash; both ask the same question of your role.
            </p>
        </section>

        <section class="panel-card">
            <header class="card-head"><h2>How things work</h2></header>

            <dl class="faq">
                <dt>Why was my booking refused as a double booking?</dt>
                <dd>
                    A dentist can only be in one place at a time. The check uses the treatment's
                    duration, not just its start time, so a 90-minute treatment at 10:00 blocks
                    10:30 as well. Use the dentist's schedule page to find a free slot.
                </dd>

                <dt>Why can I not bill this appointment?</dt>
                <dd>
                    Only a completed visit can be billed &mdash; a booking is not income. Record
                    the outcome first. An appointment can be billed once and only once.
                </dd>

                <dt>Why can I not change a cancelled appointment?</dt>
                <dd>
                    Cancelled, completed and no-show are final. Without that rule a cancelled visit
                    could be flipped to completed and billed, putting revenue in the reports for a
                    visit that never happened.
                </dd>

                <dt>Why is the patient's name not editable?</dt>
                <dd>
                    A patient is identified by name and contact number together. Changing the name
                    casually would split one person's history in two, or merge two people into one.
                </dd>

                <dt>What happens to a deactivated staff account?</dt>
                <dd>
                    It cannot sign in from that moment on. It is never deleted, because every
                    appointment records who booked it and that record has to survive.
                </dd>
            </dl>
        </section>
    </div>

    <section class="panel-card">
        <header class="card-head"><h2>Clinic settings</h2></header>
        <p class="fineprint">
            Read from the database, not from the application. Changing one here changes what the
            system actually does.
        </p>

        <div class="table-scroll">
            <table class="grid">
                <caption class="visually-hidden">Clinic configuration settings</caption>
                <thead>
                <tr>
                    <th scope="col">Setting</th>
                    <th scope="col">Value</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="setting" items="${settings}">
                    <tr>
                        <td class="mono"><c:out value="${setting.key}"/></td>
                        <td><c:out value="${setting.value}"/></td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
    </section>

</t:page>
