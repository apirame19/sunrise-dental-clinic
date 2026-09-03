<%--
    The page every deliberate failure lands on: refused permission, a missing appointment, an
    unreachable database.

    It shows the sentence BaseServlet was given and nothing else. A stack trace or a SQL message
    here would be both alarming to a receptionist and an information disclosure - DataAccessException
    keeps its detailed message for the log and hands this page a plain one.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="${errorHeading}">

    <div class="panel-card error-card">
        <p class="status-code">Error <c:out value="${errorStatus}"/></p>
        <p class="lead"><c:out value="${errorMessage}"/></p>

        <div class="actions">
            <a class="button primary" href="${pageContext.request.contextPath}/app/dashboard">
                Return to the dashboard
            </a>
            <a class="button ghost" href="${pageContext.request.contextPath}/app/help">
                Help
            </a>
        </div>
    </div>

</t:page>
