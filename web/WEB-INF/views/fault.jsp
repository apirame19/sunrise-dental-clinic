<%--
    The last resort: anything that escaped the filter chain before a servlet could handle it.

    Every failure the application raises on purpose is caught by BaseServlet and rendered as a
    sentence. Reaching this page means something unforeseen happened, so it says exactly that and
    nothing more. The exception is not printed: the container has already logged it, and a Java
    stack trace on screen tells an attacker the framework versions and the package layout while
    telling the receptionist nothing at all.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Something went wrong</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/main.css">
</head>
<body class="centred">
<main class="panel">
    <p class="status-code">Error 500</p>
    <h1>Something went wrong</h1>
    <p class="notice">
        The system could not complete that request. Nothing has been saved. Please try again, and
        tell the clinic administrator if it keeps happening.
    </p>
    <p><a class="button primary" href="${pageContext.request.contextPath}/app/dashboard">
        Return to the dashboard</a></p>
</main>
</body>
</html>
