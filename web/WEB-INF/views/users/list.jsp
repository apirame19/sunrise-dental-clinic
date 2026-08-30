<%--
    Staff accounts. Administrators only - enforced by the authorisation filter and again by the
    facade, not by this file hiding a link.

    There is no delete button, and there is no delete anywhere behind this screen. Every
    appointment records who booked it under a foreign key that refuses deletion, so removing an
    account would either fail or destroy the record of who did what. Deactivation is the removal
    this system offers: a deactivated account is refused at sign-in immediately, while everything
    that person ever booked stays attributable to them.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Staff accounts" subtitle="Who may use the clinic system." wide="true">

    <div class="bar-form split">
        <p class="result-count">
            ${staff.size()} ${staff.size() eq 1 ? 'account' : 'accounts'}
        </p>
        <a class="button primary" href="${pageContext.request.contextPath}/app/users/add">
            Add a staff account
        </a>
    </div>

    <section class="panel-card">
        <div class="table-scroll">
            <table class="grid">
                <caption class="visually-hidden">Staff accounts</caption>
                <thead>
                <tr>
                    <th scope="col">Username</th>
                    <th scope="col">Name</th>
                    <th scope="col">Role</th>
                    <th scope="col">Status</th>
                    <th scope="col">Last signed in</th>
                    <th scope="col">Actions</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach var="account" items="${staff}">
                    <tr class="${account.active ? '' : 'inactive'}">
                        <td class="mono"><c:out value="${account.username}"/></td>
                        <td>
                            <c:out value="${account.fullName}"/>
                            <c:if test="${account.userId eq sessionUser.userId}">
                                <span class="pill small">You</span>
                            </c:if>
                        </td>
                        <td><c:out value="${account.role.label}"/></td>
                        <td>
                            <span class="pill ${account.active ? 'status-COMPLETED' : 'status-CANCELLED'}">
                                ${account.active ? 'Active' : 'Deactivated'}
                            </span>
                            <c:if test="${account.locked}">
                                <span class="pill status-NO_SHOW">Locked out</span>
                            </c:if>
                        </td>
                        <td class="nowrap muted">
                            <c:choose>
                                <c:when test="${not empty account.lastLoginAt}">
                                    <c:out value="${account.lastLoginAt}"/>
                                </c:when>
                                <c:otherwise>Never</c:otherwise>
                            </c:choose>
                        </td>
                        <td class="nowrap">
                            <a href="${pageContext.request.contextPath}/app/users/edit?id=${account.userId}">
                                Amend
                            </a>
                            &middot;
                            <a href="${pageContext.request.contextPath}/app/users/password?id=${account.userId}">
                                Reset password
                            </a>
                            &middot;
                            <%--
                                Deactivation is a state change, so it is a POST carrying the token.
                                The button is not shown against your own account - the business
                                tier refuses that anyway, but offering a control that always fails
                                is its own kind of defect.
                            --%>
                            <c:choose>
                                <c:when test="${account.userId eq sessionUser.userId}">
                                    <span class="muted">&mdash;</span>
                                </c:when>
                                <c:otherwise>
                                    <form method="post"
                                          action="${pageContext.request.contextPath}/app/users/activate?id=${account.userId}"
                                          class="inline-form">
                                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                                        <input type="hidden" name="active"
                                               value="${account.active ? 'false' : 'true'}">
                                        <button type="submit" class="link-button">
                                            ${account.active ? 'Deactivate' : 'Reactivate'}
                                        </button>
                                    </form>
                                </c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
    </section>

    <p class="fineprint">
        Accounts are never deleted. A deactivated account cannot sign in, but everything it booked
        remains attributable to it.
    </p>

</t:page>
