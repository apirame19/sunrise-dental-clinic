<%--
    The practice roster.

    Withdrawn dentists are listed too, marked as such. They still own every appointment they ever
    saw, and hiding them would make those historical records look as though they belonged to
    nobody. What withdrawal actually does is remove someone from the booking dropdown - which is
    the only thing the clinic needs it to do.

    The add, amend and withdraw controls appear only for an administrator. The same Role predicate
    is checked by the authorisation filter and again by the facade, so hiding them here is a
    courtesy rather than the protection.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Dentists" subtitle="Who practises at the clinic." wide="true">

    <c:if test="${sessionUser.role.canManageMasterData()}">
        <div class="bar-form split">
            <span></span>
            <a class="button primary" href="${pageContext.request.contextPath}/app/dentists/add">
                Add a dentist
            </a>
        </div>
    </c:if>

    <section class="panel-card">
        <c:choose>
            <c:when test="${empty dentists}">
                <p class="empty">No dentists are on the roster yet.</p>
            </c:when>
            <c:otherwise>
                <div class="table-scroll">
                    <table class="grid">
                        <caption class="visually-hidden">Dentists at the clinic</caption>
                        <thead>
                        <tr>
                            <th scope="col">Name</th>
                            <th scope="col">Specialisation</th>
                            <th scope="col">SLMC registration</th>
                            <th scope="col">Contact</th>
                            <th scope="col">Status</th>
                            <th scope="col">Actions</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="dentist" items="${dentists}">
                            <tr class="${dentist.active ? '' : 'inactive'}">
                                <td><c:out value="${dentist.dentistName}"/></td>
                                <td><c:out value="${dentist.specialization}"/></td>
                                <td class="nowrap"><c:out value="${dentist.licenseNo}"/></td>
                                <td class="nowrap">
                                    <c:choose>
                                        <c:when test="${not empty dentist.contactNumber}">
                                            <c:out value="${dentist.contactNumber}"/>
                                        </c:when>
                                        <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <span class="pill ${dentist.active ? 'status-COMPLETED' : 'status-CANCELLED'}">
                                        ${dentist.active ? 'Practising' : 'Withdrawn'}
                                    </span>
                                </td>
                                <td class="nowrap">
                                    <a href="${pageContext.request.contextPath}/app/dentists/schedule?id=${dentist.dentistId}">
                                        Schedule
                                    </a>

                                    <c:if test="${sessionUser.role.canManageMasterData()}">
                                        &middot;
                                        <a href="${pageContext.request.contextPath}/app/dentists/edit?id=${dentist.dentistId}">
                                            Amend
                                        </a>
                                        &middot;
                                        <%-- A state change, so a POST with the token, not a link. --%>
                                        <form method="post"
                                              action="${pageContext.request.contextPath}/app/dentists/activate?id=${dentist.dentistId}"
                                              class="inline-form">
                                            <input type="hidden" name="csrfToken" value="${csrfToken}">
                                            <input type="hidden" name="active"
                                                   value="${dentist.active ? 'false' : 'true'}">
                                            <button type="submit" class="link-button">
                                                ${dentist.active ? 'Withdraw' : 'Reinstate'}
                                            </button>
                                        </form>
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
