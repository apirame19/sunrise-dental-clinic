<%--
    The treatment catalogue and price list.

    The indicative total in the last column came from the business tier, not from arithmetic in
    this file. A JSP that added the consultation fee and worked out the levy itself would be a
    second implementation of the pricing rules, and the two would eventually disagree by a cent -
    which is precisely the class of defect this system was commissioned to remove.

    "Indicative" is meant literally. The authoritative figure for any given visit comes from the
    billing screen, which knows whether that visit qualifies as a follow-up.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>

<t:page title="Treatments and prices"
        subtitle="What the clinic offers, and what it costs." wide="true">

    <p class="alert info" role="note">
        Every price below is read from the clinic's own price list. A consultation fee of
        <c:out value="${currency}"/>
        <fmt:formatNumber value="${consultationFee}" minFractionDigits="2" maxFractionDigits="2"/>
        is added once per bill, and a health service levy of
        <fmt:formatNumber value="${taxRate}" maxFractionDigits="2"/>%
        applies to treatments that are not exempt. Preventive care is exempt.
    </p>

    <section class="panel-card">
        <c:choose>
            <c:when test="${empty treatments}">
                <p class="empty">The price list is empty.</p>
            </c:when>
            <c:otherwise>
                <div class="table-scroll">
                    <table class="grid">
                        <caption class="visually-hidden">Treatments offered by the clinic</caption>
                        <thead>
                        <tr>
                            <th scope="col">Code</th>
                            <th scope="col">Treatment</th>
                            <th scope="col">Description</th>
                            <th scope="col" class="right">List price</th>
                            <th scope="col" class="right">Duration</th>
                            <th scope="col">Levy</th>
                            <th scope="col" class="right">Indicative total</th>
                            <th scope="col">Offered</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="treatment" items="${treatments}">
                            <tr class="${treatment.active ? '' : 'inactive'}">
                                <td class="nowrap mono"><c:out value="${treatment.treatmentCode}"/></td>
                                <td><c:out value="${treatment.treatmentName}"/></td>
                                <td class="muted"><c:out value="${treatment.description}"/></td>
                                <td class="right nowrap">
                                    <c:out value="${currency}"/>
                                    <fmt:formatNumber value="${treatment.baseCost}"
                                                      minFractionDigits="2" maxFractionDigits="2"
                                                      groupingUsed="true"/>
                                </td>
                                <td class="right nowrap">${treatment.durationMinutes} min</td>
                                <td>
                                    <span class="pill small ${treatment.taxable ? '' : 'muted-pill'}">
                                        ${treatment.taxable ? 'Applies' : 'Exempt'}
                                    </span>
                                </td>
                                <td class="right nowrap strong">
                                    <c:out value="${currency}"/>
                                    <fmt:formatNumber value="${indicativeTotals[treatment.treatmentId]}"
                                                      minFractionDigits="2" maxFractionDigits="2"
                                                      groupingUsed="true"/>
                                </td>
                                <td>
                                    <span class="pill ${treatment.active ? 'status-COMPLETED' : 'status-CANCELLED'}">
                                        ${treatment.active ? 'Yes' : 'Discontinued'}
                                    </span>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </section>

</t:page>
