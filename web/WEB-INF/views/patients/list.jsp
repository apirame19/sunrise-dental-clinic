<%--
    The patient register: search, or browse everyone.

    Search matches on name or contact number, because those are the two things a caller can offer
    over the telephone. A blank search shows the whole list rather than nothing, so the page is
    useful before anyone has typed anything.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Patients" subtitle="Every patient on file." wide="true">

    <div class="bar-form split">
        <form method="get" action="${pageContext.request.contextPath}/app/patients"
              class="bar-form" role="search">
            <label for="q">Search by name or number</label>
            <input type="search" id="q" name="q" maxlength="100"
                   value="<c:out value='${searchTerm}'/>">
            <button type="submit" class="button primary">Search</button>
            <c:if test="${not empty searchTerm}">
                <a class="button ghost small"
                   href="${pageContext.request.contextPath}/app/patients">Clear</a>
            </c:if>
        </form>

        <c:if test="${sessionUser.role.canRegisterAppointments()}">
            <a class="button primary"
               href="${pageContext.request.contextPath}/app/patients/register">
                Register a patient
            </a>
        </c:if>
    </div>

    <section class="panel-card">
        <c:choose>
            <c:when test="${empty patients}">
                <p class="empty">
                    <c:choose>
                        <c:when test="${not empty searchTerm}">
                            No patient matches &ldquo;<c:out value="${searchTerm}"/>&rdquo;.
                        </c:when>
                        <c:otherwise>No patients are registered yet.</c:otherwise>
                    </c:choose>
                </p>
            </c:when>
            <c:otherwise>
                <p class="result-count">
                    ${patients.size()} ${patients.size() eq 1 ? 'patient' : 'patients'}
                </p>
                <div class="table-scroll">
                    <table class="grid">
                        <caption class="visually-hidden">Registered patients</caption>
                        <thead>
                        <tr>
                            <th scope="col">Name</th>
                            <th scope="col">Contact number</th>
                            <th scope="col">Address</th>
                            <th scope="col">Email</th>
                            <th scope="col">Record</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="patient" items="${patients}">
                            <tr>
                                <td><c:out value="${patient.patientName}"/></td>
                                <td class="nowrap"><c:out value="${patient.contactNumber}"/></td>
                                <td><c:out value="${patient.address}"/></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${not empty patient.email}">
                                            <c:out value="${patient.email}"/>
                                        </c:when>
                                        <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <td class="nowrap">
                                    <a href="${pageContext.request.contextPath}/app/patients/view?id=${patient.patientId}">
                                        History
                                    </a>
                                    <c:if test="${sessionUser.role.canRegisterAppointments()}">
                                        &middot;
                                        <a href="${pageContext.request.contextPath}/app/patients/edit?id=${patient.patientId}">
                                            Amend
                                        </a>
                                    </c:if>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </section>

</t:page>
