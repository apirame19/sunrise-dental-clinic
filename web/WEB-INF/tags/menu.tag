<%--
    COMPOSITE, rendered.

    One tag renders a MenuComponent whatever its shape, calling itself for each child. An item is
    a link; a group is a heading with a nested list. The tree arrives already pruned to the
    signed-in user's role by MenuComponent.filterFor, so this file contains no permission logic at
    all - it cannot, because it is never given anything the viewer may not use.

    This is the payoff the pattern was chosen for. Adding a fourth report or a whole new section to
    Navigation.build() changes nothing here.
--%>
<%@ tag description="Recursive navigation renderer" pageEncoding="UTF-8"
        trimDirectiveWhitespaces="true" %>
<%@ attribute name="node" required="true"
        type="lk.sunrisedental.patterns.composite.MenuComponent" %>
<%@ attribute name="active" required="false" type="java.lang.String" %>
<%@ attribute name="root" required="false" type="java.lang.Boolean" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<c:choose>

    <%-- A leaf: one clickable entry. --%>
    <c:when test="${node.item}">
        <li>
            <a href="${pageContext.request.contextPath}${node.url}"
               class="nav-link ${active eq node.url ? 'current' : ''}"
               <c:if test="${active eq node.url}">aria-current="page"</c:if>>
                <span class="nav-icon" aria-hidden="true"><c:out value="${node.icon}"/></span>
                <c:out value="${node.label}"/>
            </a>
        </li>
    </c:when>

    <%-- The root group: its own label is the brand, already shown, so only its children print. --%>
    <c:when test="${root}">
        <ul class="nav-root">
            <c:forEach var="child" items="${node.children}">
                <t:menu node="${child}" active="${active}"/>
            </c:forEach>
        </ul>
    </c:when>

    <%-- A nested group: a heading and its own list, to any depth. --%>
    <c:otherwise>
        <li class="nav-group">
            <span class="nav-heading">
                <span class="nav-icon" aria-hidden="true"><c:out value="${node.icon}"/></span>
                <c:out value="${node.label}"/>
            </span>
            <ul>
                <c:forEach var="child" items="${node.children}">
                    <t:menu node="${child}" active="${active}"/>
                </c:forEach>
            </ul>
        </li>
    </c:otherwise>

</c:choose>
