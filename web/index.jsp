<%--
    The welcome file. It renders nothing of its own: it sends the visitor to the dashboard, and
    the authentication filter turns that into the login page for anyone not signed in.

    Keeping the decision there rather than here means there is one rule about who may see the
    application, applied in one place, instead of a second copy of it in a JSP.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:redirect url="/app/dashboard"/>
