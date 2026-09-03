<%--
    Container-level 404: a URL matching no servlet.

    Standalone rather than using the application shell, because nothing has run to attach the
    navigation or the clinic settings to this request - a mistyped URL never reaches a servlet.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" isErrorPage="false" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Page not found</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/main.css">
</head>
<body class="centred">
<main class="panel">
    <p class="status-code">Error 404</p>
    <h1>Page not found</h1>
    <p class="notice">
        There is no page at that address. It may have been mistyped, or the link that brought you
        here may be out of date.
    </p>
    <p><a class="button primary" href="${pageContext.request.contextPath}/app/dashboard">
        Return to the dashboard</a></p>
</main>
</body>
</html>
