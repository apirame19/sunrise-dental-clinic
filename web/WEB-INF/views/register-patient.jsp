<%--
    Patient self-registration.

    Deliberately outside the application shell, for the same reason as the sign-in screen: the
    person filling this in has no account yet, so there is no role to build a navigation menu from.

    There is NO role field on this form, and none is accepted by the servlet behind it. The role is
    fixed as PATIENT in RegistrationService, so there is nothing here an attacker could change to
    obtain a different one. There is likewise no administrator registration anywhere in this
    application.

    Every typed value is echoed back after a rejection so nothing is lost - except the password,
    which never is. Repopulating a password field puts the credential into the page source, the
    browser's back cache and any proxy log between here and there.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Register as a patient &middot; <c:out value="${clinic.clinicName}"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/main.css">
</head>
<body class="centred">

<main class="panel login-panel">

    <div class="login-brand">
        <span class="brand-mark large" aria-hidden="true">◐</span>
        <h1><c:out value="${clinic.clinicName}"/></h1>
        <p class="subtitle">Register as a patient</p>
    </div>

    <c:if test="${not empty validation}">
        <t:errors validation="${validation}"/>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/register/patient"
          class="stacked-form">
        <input type="hidden" name="csrfToken" value="${csrfToken}">

        <div class="field">
            <label for="fullName">Full name</label>
            <input type="text" id="fullName" name="fullName" required maxlength="100"
                   autofocus autocomplete="name"
                   value="<c:out value='${fullName}'/>"
                   <c:if test="${not empty validation.fieldErrors['fullName']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="fullName"/>
        </div>

        <div class="field">
            <label for="address">Address</label>
            <input type="text" id="address" name="address" required maxlength="255"
                   autocomplete="street-address"
                   value="<c:out value='${address}'/>"
                   <c:if test="${not empty validation.fieldErrors['address']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="address"/>
        </div>

        <div class="field">
            <label for="contactNumber">Contact number</label>
            <input type="tel" id="contactNumber" name="contactNumber" required maxlength="20"
                   autocomplete="tel"
                   value="<c:out value='${contactNumber}'/>"
                   <c:if test="${not empty validation.fieldErrors['contactNumber']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="contactNumber"/>
        </div>

        <div class="field">
            <label for="email">Email <span class="optional">(optional)</span></label>
            <input type="email" id="email" name="email" maxlength="120" autocomplete="email"
                   value="<c:out value='${email}'/>"
                   <c:if test="${not empty validation.fieldErrors['email']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="email"/>
        </div>

        <div class="field">
            <label for="username">Choose a username</label>
            <input type="text" id="username" name="username" required maxlength="50"
                   autocomplete="username"
                   value="<c:out value='${username}'/>"
                   <c:if test="${not empty validation.fieldErrors['username']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="username"/>
        </div>

        <div class="field">
            <label for="password">Choose a password</label>
            <input type="password" id="password" name="password" required maxlength="200"
                   autocomplete="new-password" aria-describedby="password-hint password-error"
                   <c:if test="${not empty validation.fieldErrors['password']}">aria-invalid="true"</c:if>>
            <p class="hint" id="password-hint">
                At least 10 characters, including letters, digits and one other character.
            </p>
            <t:fieldError validation="${validation}" field="password"/>
        </div>

        <button type="submit" class="button primary block">Create my account</button>
    </form>

    <p class="fineprint">
        Already registered?
        <a href="${pageContext.request.contextPath}/login">Sign in</a>.
        Are you a dentist?
        <a href="${pageContext.request.contextPath}/register/dentist">Register as a dentist</a>.
    </p>
</main>

</body>
</html>
