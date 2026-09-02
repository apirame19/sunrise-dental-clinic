<%--
    All six reports, one screen.

    The report body is a single call to the recursive <t:reportNode> fragment. It does not matter
    that the daily report is three levels deep, the billing summary two and the clinic overview
    four - a Composite tree renders the same way whatever its shape, and every subtotal shown is
    derived by walking the rows printed beneath it.

    The report chooser only lists what this user may run. Those links are the same Role predicates
    the facade enforces, so nobody is offered a report that would refuse them.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="${report.title}" subtitle="${report.subtitle}" wide="true">

    <nav class="report-tabs" aria-label="Reports">
        <a class="tab ${reportType eq 'daily' ? 'current' : ''}"
           href="${pageContext.request.contextPath}/app/reports?type=daily">Daily appointments</a>
        <a class="tab ${reportType eq 'dentist' ? 'current' : ''}"
           href="${pageContext.request.contextPath}/app/reports?type=dentist">Dentist workload</a>
        <a class="tab ${reportType eq 'status' ? 'current' : ''}"
           href="${pageContext.request.contextPath}/app/reports?type=status">Appointment status</a>

        <c:if test="${sessionUser.role.canViewFinancialReports()}">
            <a class="tab ${reportType eq 'treatment' ? 'current' : ''}"
               href="${pageContext.request.contextPath}/app/reports?type=treatment">Treatment revenue</a>
            <a class="tab ${reportType eq 'billing' ? 'current' : ''}"
               href="${pageContext.request.contextPath}/app/reports?type=billing">Billing summary</a>
            <a class="tab ${reportType eq 'overview' ? 'current' : ''}"
               href="${pageContext.request.contextPath}/app/reports?type=overview">Clinic overview</a>
        </c:if>
    </nav>

    <form method="get" action="${pageContext.request.contextPath}/app/reports" class="bar-form">
        <input type="hidden" name="type" value="${reportType}">

        <c:choose>
            <c:when test="${singleDate}">
                <label for="from">Day</label>
                <input type="date" id="from" name="from" value="${from}">
            </c:when>
            <c:otherwise>
                <label for="from">From</label>
                <input type="date" id="from" name="from" value="${from}">
                <label for="to">To</label>
                <input type="date" id="to" name="to" value="${to}">
            </c:otherwise>
        </c:choose>

        <c:if test="${reportType eq 'dentist'}">
            <label for="dentistId">Dentist</label>
            <select id="dentistId" name="dentistId">
                <option value="">All dentists</option>
                <c:forEach var="dentist" items="${dentists}">
                    <option value="${dentist.dentistId}"
                            ${dentistId eq dentist.dentistId ? 'selected' : ''}>
                        <c:out value="${dentist.dentistName}"/>
                    </option>
                </c:forEach>
            </select>
        </c:if>

        <button type="submit" class="button primary">Run report</button>
    </form>

    <section class="panel-card report">
        <%-- COMPOSITE: one fragment, any depth, subtotals at every level. --%>
        <t:reportNode node="${report}" currency="${currency}"/>
    </section>

    <p class="fineprint no-print">
        Subtotals are computed by walking the rows printed beneath them, so a section total can
        never disagree with its own contents. Use your browser's print command for a paper copy.
    </p>

</t:page>
