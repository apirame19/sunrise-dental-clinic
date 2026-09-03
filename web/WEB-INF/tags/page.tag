<%--
    The application shell: every signed-in page is written as <t:page>...content...</t:page>.

    A tag file rather than a header.jsp / footer.jsp pair, because that pair splits an <html>
    element across two files - the opening tag lives in one and the closing tag in another, and
    nothing checks they still match. A tag file wraps its body, so the document cannot be left
    half-closed by a view that forgets the second include.

    The furniture rendered here (navigation, user badge, flash messages, footer) is attached to
    every request by BaseServlet.render, so no view fetches it and no view can omit it.

    There is no JavaScript in this file, or anywhere in this application. Every interaction is a
    plain form or a link.
--%>
<%@ tag description="Application page shell" pageEncoding="UTF-8" trimDirectiveWhitespaces="true" %>
<%@ attribute name="title" required="true" type="java.lang.String" %>
<%@ attribute name="subtitle" required="false" type="java.lang.String" %>
<%@ attribute name="wide" required="false" type="java.lang.Boolean" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${title}"/> &middot; <c:out value="${clinic.clinicName}"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/main.css">
</head>
<body class="app">

<a class="skip-link" href="#content">Skip to main content</a>

<div class="shell">

    <nav class="sidebar" aria-label="Main navigation">
        <div class="brand">
            <span class="brand-mark" aria-hidden="true">◐</span>
            <span class="brand-text">
                <strong><c:out value="${clinic.clinicName}"/></strong>
                <small>Appointments &amp; Patients</small>
            </span>
        </div>

        <%-- COMPOSITE: one recursive tag renders the whole role-filtered menu tree. --%>
        <c:if test="${not empty nav}">
            <t:menu node="${nav}" active="${activeNav}" root="true"/>
        </c:if>
    </nav>

    <div class="main">

        <header class="topbar">
            <div class="topbar-title">
                <h1><c:out value="${title}"/></h1>
                <c:if test="${not empty subtitle}">
                    <p class="subtitle"><c:out value="${subtitle}"/></p>
                </c:if>
            </div>

            <div class="topbar-user">
                <c:if test="${not empty sessionUser}">
                    <span class="who">
                        <strong><c:out value="${sessionUser.fullName}"/></strong>
                        <small><c:out value="${sessionUser.role.label}"/></small>
                    </span>
                    <%-- Sign-out is a POST carrying the token, like every other state change. --%>
                    <form method="post" action="${pageContext.request.contextPath}/logout"
                          class="inline-form">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <button type="submit" class="button ghost">Sign out</button>
                    </form>
                </c:if>
            </div>
        </header>

        <main id="content" class="content ${wide ? 'wide' : ''}">

            <c:if test="${not empty flashSuccess}">
                <p class="alert success" role="status"><c:out value="${flashSuccess}"/></p>
            </c:if>
            <c:if test="${not empty flashError}">
                <p class="alert error" role="alert"><c:out value="${flashError}"/></p>
            </c:if>

            <jsp:doBody/>

        </main>

        <footer class="footer">
            <span><c:out value="${clinic.clinicName}"/> &middot;
                  <c:out value="${clinic.clinicAddress}"/> &middot;
                  <c:out value="${clinic.clinicPhone}"/></span>
            <span>CIS6003 Advanced Programming &middot; WRIT1</span>
        </footer>
    </div>
</div>

</body>
</html>
