<%--
    The staff sign-in screen.

    Deliberately outside the application shell: there is no navigation to show, and rendering a
    menu here would mean deciding what an unauthenticated visitor's role is.

    The username is echoed back after a failure so the user only retypes the part that was wrong.
    The password never is - repopulating a password field puts the credential into the page source,
    the browser's back cache and any proxy log between here and there.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Sign in &middot; <c:out value="${clinic.clinicName}"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/main.css">
</head>
<body class="centred">

<main class="panel login-panel">

    <div class="login-brand">
        <span class="brand-mark large" aria-hidden="true">◐</span>
        <h1><c:out value="${clinic.clinicName}"/></h1>
        <p class="subtitle">Appointment and Patient Management System</p>
    </div>

    <c:if test="${not empty flashSuccess}">
        <p class="alert success" role="status"><c:out value="${flashSuccess}"/></p>
    </c:if>
    <c:if test="${not empty notice}">
        <p class="alert info" role="status"><c:out value="${notice}"/></p>
    </c:if>
    <c:if test="${not empty errorMessage}">
        <p class="alert error" role="alert"><c:out value="${errorMessage}"/></p>
    </c:if>

    <form method="post" action="${pageContext.request.contextPath}/login" class="stacked-form">
        <input type="hidden" name="csrfToken" value="${csrfToken}">

        <div class="field">
            <label for="username">Username</label>
            <input type="text" id="username" name="username" required autofocus
                   autocomplete="username" maxlength="50"
                   value="<c:out value='${username}'/>">
        </div>

        <div class="field">
            <label for="password">Password</label>
            <input type="password" id="password" name="password" required
                   autocomplete="current-password" maxlength="200">
        </div>

        <button type="submit" class="button primary block">Sign in</button>
    </form>

    <%--
        Registration is offered for patients and for dentists only. There is deliberately no
        administrator registration link: the administrator account is seeded in the database and
        cannot be created through the application.
    --%>
    <nav class="register-links" aria-label="Registration">
        <p>New here?</p>
        <a class="button ghost block"
           href="${pageContext.request.contextPath}/register/patient">Register as a patient</a>
        <a class="button ghost block"
           href="${pageContext.request.contextPath}/register/dentist">Register as a dentist</a>
    </nav>

    <p class="fineprint">
        Staff sign-in attempts are recorded. A new dentist account must be approved by the clinic
        administrator before it can be used.
    </p>
</main>

</body>
</html>
