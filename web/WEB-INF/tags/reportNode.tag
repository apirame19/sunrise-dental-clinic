<%--
    COMPOSITE, rendered - the second and larger payoff.

    Every report in this application is a ReportSection tree, and they are not the same shape: the
    daily report is three levels (report -> dentist -> appointment), the billing summary is two,
    and the clinic overview is four because it nests whole reports inside itself. This one file
    renders all of them, because a section and a line answer the same questions - title, subtitle,
    total, count, children.

    The subtotal printed against a section is ReportSection.getTotal(), which walks its children
    every time it is asked. It therefore cannot disagree with the rows printed beneath it. That is
    the property the clinic actually needed: reports whose totals add up.

    Columns on a leaf are an open map rather than fixed fields, so a report can carry whatever
    columns it needs without a class per report and without this file knowing any of their names.
--%>
<%@ tag description="Recursive report tree renderer" pageEncoding="UTF-8"
        trimDirectiveWhitespaces="true" %>
<%@ attribute name="node" required="true"
        type="lk.sunrisedental.patterns.composite.ReportComponent" %>
<%@ attribute name="currency" required="false" type="java.lang.String" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<c:choose>

    <%-- A leaf: one row, plus whatever columns this particular report attached to it. --%>
    <c:when test="${node.leaf}">
        <div class="report-line depth-${node.depth}">
            <div class="report-line-head">
                <span class="report-title"><c:out value="${node.title}"/></span>
                <c:if test="${not empty node.subtitle}">
                    <span class="report-subtitle"><c:out value="${node.subtitle}"/></span>
                </c:if>
                <span class="report-amount">
                    <c:out value="${currency}"/>
                    <fmt:formatNumber value="${node.total}" minFractionDigits="2"
                                      maxFractionDigits="2" groupingUsed="true"/>
                </span>
            </div>
            <c:if test="${not empty node.columns}">
                <dl class="report-columns">
                    <c:forEach var="column" items="${node.columns}">
                        <div class="report-column">
                            <dt><c:out value="${column.key}"/></dt>
                            <dd><c:out value="${column.value}"/></dd>
                        </div>
                    </c:forEach>
                </dl>
            </c:if>
        </div>
    </c:when>

    <%-- A branch: a heading, its children, and a subtotal derived from exactly those children. --%>
    <c:otherwise>
        <section class="report-section depth-${node.depth}">
            <header class="report-section-head">
                <div>
                    <h3><c:out value="${node.title}"/></h3>
                    <c:if test="${not empty node.subtitle}">
                        <p class="report-subtitle"><c:out value="${node.subtitle}"/></p>
                    </c:if>
                </div>
                <div class="report-section-total">
                    <span class="count">
                        <c:out value="${node.count}"/>
                        ${node.count eq 1 ? 'record' : 'records'}
                    </span>
                    <strong>
                        <c:out value="${currency}"/>
                        <fmt:formatNumber value="${node.total}" minFractionDigits="2"
                                          maxFractionDigits="2" groupingUsed="true"/>
                    </strong>
                </div>
            </header>

            <c:choose>
                <c:when test="${empty node.children}">
                    <p class="empty">Nothing to report in this section.</p>
                </c:when>
                <c:otherwise>
                    <div class="report-children">
                        <c:forEach var="child" items="${node.children}">
                            <t:reportNode node="${child}" currency="${currency}"/>
                        </c:forEach>
                    </div>
                </c:otherwise>
            </c:choose>
        </section>
    </c:otherwise>

</c:choose>
