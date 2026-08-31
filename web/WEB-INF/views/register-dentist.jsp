<%--
    Dentist self-registration.

    As with the patient form there is NO role field, and none is accepted by the servlet behind it:
    the role is fixed as DENTIST in RegistrationService. There is no administrator registration
    anywhere in this application.

    The notice below is not decoration. A dentist login can read every patient record, so the
    account is created inactive and an administrator must approve it. Saying so here means an
    applicant who then cannot sign in understands why, instead of assuming the form failed.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Register as a dentist &middot; <c:out value="${clinic.clinicName}"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/main.css">
</head>
<body class="centred">

<main class="panel login-panel">

    <div class="login-brand">
        <span class="brand-mark large" aria-hidden="true">◐</span>
        <h1><c:out value="${clinic.clinicName}"/></h1>
        <p class="subtitle">Register as a dentist</p>
    </div>

    <p class="alert info" role="status">
        A dentist account can see patient records, so it is checked before it is switched on.
        Your registration is sent to the clinic administrator, and you will be able to sign in
        once they have approved it.
    </p>

    <c:if test="${not empty validation}">
        <t:errors validation="${validation}"/>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/register/dentist"
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
            <label for="specialization">Specialisation</label>
            <input type="text" id="specialization" name="specialization" required maxlength="100"
                   autocomplete="off"
                   value="<c:out value='${specialization}'/>"
                   <c:if test="${not empty validation.fieldErrors['specialization']}">aria-invalid="true"</c:if>>
            <t:fieldError validation="${validation}" field="specialization"/>
        </div>

        <div class="field">
            <label for="licenseNo">Registration number</label>
            <input type="text" id="licenseNo" name="licenseNo" required maxlength="30"
                   autocomplete="off" aria-describedby="licenseNo-hint licenseNo-error"
                   value="<c:out value='${licenseNo}'/>"
                   <c:if test="${not empty validation.fieldErrors['licenseNo']}">aria-invalid="true"</c:if>>
            <p class="hint" id="licenseNo-hint">
                Your Sri Lanka Medical Council registration number, for example SLMC-D-1042.
            </p>
            <t:fieldError validation="${validation}" field="licenseNo"/>
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

        <button type="submit" class="button primary block">Submit registration</button>
    </form>

    <p class="fineprint">
        Already have an account?
        <a href="${pageContext.request.contextPath}/login">Sign in</a>.
        Are you a patient?
        <a href="${pageContext.request.contextPath}/register/patient">Register as a patient</a>.
    </p>
</main>

</body>
</html>
