<%--
    Amend a staff account's name or role.

    The username is shown but fixed. It is what the sign-in audit trail records against every
    attempt, and letting it change would make that trail ambiguous - the same history would appear
    to belong to two different people.

    Demoting the last remaining administrator is refused by the business tier, because it has
    exactly the same effect as deactivating them: nobody left who can reach this screen.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Amend staff account" subtitle="${staffMember.fullName}">

    <c:if test="${not empty validation}">
        <t:errors validation="${validation}"/>
    </c:if>

    <form method="post"
          action="${pageContext.request.contextPath}/app/users/edit?id=${staffMember.userId}"
          class="stacked-form card-form">
        <input type="hidden" name="csrfToken" value="${csrfToken}">

        <div class="field">
            <label for="username">Username</label>
            <input type="text" id="username" readonly disabled
                   value="<c:out value='${staffMember.username}'/>"
                   aria-describedby="username-hint">
            <p class="hint" id="username-hint">
                Fixed: the sign-in audit trail is recorded against it.
            </p>
        </div>

        <div class="field">
            <label for="fullName">Full name</label>
            <input type="text" id="fullName" name="fullName" required maxlength="100" autofocus
                   value="<c:out value='${staffMember.fullName}'/>"
                   <c:if test="${not empty validation.fieldErrors['fullName']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="fullName"/>
        </div>

        <div class="field">
            <label for="role">Role</label>
            <select id="role" name="role" required
                    <c:if test="${not empty validation.fieldErrors['role']}">aria-invalid="true"</c:if>>
                <c:forEach var="option" items="${roles}">
                    <option value="${option}" ${staffMember.role eq option ? 'selected' : ''}>
                        <c:out value="${option.label}"/>
                    </option>
                </c:forEach>
            </select>
            <t:fieldError validation="${validation}" field="role"/>
        </div>

        <div class="actions">
            <button type="submit" class="button primary">Save changes</button>
            <a class="button ghost" href="${pageContext.request.contextPath}/app/users">Cancel</a>
        </div>
    </form>

    <section class="panel-card">
        <header class="card-head"><h2>Other actions</h2></header>
        <div class="actions">
            <a class="button ghost"
               href="${pageContext.request.contextPath}/app/users/password?id=${staffMember.userId}">
                Reset this account's password
            </a>
        </div>
    </section>

</t:page>
