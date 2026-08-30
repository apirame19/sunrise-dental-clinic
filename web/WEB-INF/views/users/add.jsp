<%--
    Create a staff account.

    The password field is never repopulated after a rejection. Putting a credential back into the
    page source would leave it in the browser's back cache and in any proxy log between here and
    the server; the administrator retypes it, which costs seconds.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Add a staff account" subtitle="A new person who may use the clinic system.">

    <c:if test="${not empty validation}">
        <t:errors validation="${validation}"/>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/app/users/add"
          class="stacked-form card-form" autocomplete="off">
        <input type="hidden" name="csrfToken" value="${csrfToken}">

        <div class="field">
            <label for="username">Username</label>
            <input type="text" id="username" name="username" required maxlength="50" autofocus
                   autocomplete="off" value="<c:out value='${username}'/>"
                   aria-describedby="username-hint username-error"
                   <c:if test="${not empty validation.fieldErrors['username']}">aria-invalid="true"</c:if>>
            <p class="hint" id="username-hint">
                Starts with a letter; 3 to 50 characters of letters, digits, dots or underscores.
                It appears in the sign-in audit trail, so it should identify a person.
            </p>
            <t:fieldError validation="${validation}" field="username"/>
        </div>

        <div class="field">
            <label for="fullName">Full name</label>
            <input type="text" id="fullName" name="fullName" required maxlength="100"
                   autocomplete="off" value="<c:out value='${fullName}'/>"
                   <c:if test="${not empty validation.fieldErrors['fullName']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="fullName"/>
        </div>

        <div class="field">
            <label for="role">Role</label>
            <select id="role" name="role" required
                    aria-describedby="role-hint role-error"
                    <c:if test="${not empty validation.fieldErrors['role']}">aria-invalid="true"</c:if>>
                <option value="">Choose a role&hellip;</option>
                <c:forEach var="option" items="${roles}">
                    <option value="${option}" ${selectedRole eq option ? 'selected' : ''}>
                        <c:out value="${option.label}"/>
                    </option>
                </c:forEach>
            </select>
            <p class="hint" id="role-hint">
                Administrators manage staff, dentists and see revenue figures. Receptionists book,
                bill and run operational reports. Dentists see schedules and record outcomes.
            </p>
            <t:fieldError validation="${validation}" field="role"/>
        </div>

        <div class="field">
            <label for="password">Initial password</label>
            <input type="password" id="password" name="password" required maxlength="200"
                   autocomplete="new-password"
                   aria-describedby="password-hint password-error"
                   <c:if test="${not empty validation.fieldErrors['password']}">aria-invalid="true"</c:if>>
            <p class="hint" id="password-hint">
                At least 10 characters, containing letters, digits and at least one other
                character. It is stored as a salted PBKDF2 hash and can never be read back.
            </p>
            <t:fieldError validation="${validation}" field="password"/>
        </div>

        <div class="actions">
            <button type="submit" class="button primary">Create account</button>
            <a class="button ghost" href="${pageContext.request.contextPath}/app/users">Cancel</a>
        </div>
    </form>

</t:page>
