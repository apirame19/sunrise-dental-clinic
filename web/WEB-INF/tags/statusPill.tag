<%--
    An appointment status, rendered as a coloured pill.

    A tag rather than a copied span, because the status appears on seven screens and colour is
    doing real work here: a receptionist scanning the day list is looking for the red rows. Copying
    the markup would eventually leave one screen where "No show" is not red, which is worse than no
    colour at all.

    Colour is never the only signal. The label is always written out, so the meaning survives a
    monochrome print-out and a colour-blind reader (NFR-12).
--%>
<%@ tag description="Appointment status pill" pageEncoding="UTF-8"
        trimDirectiveWhitespaces="true" %>
<%@ attribute name="status" required="true" type="lk.sunrisedental.model.AppointmentStatus" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<span class="pill status-${status}"><c:out value="${status.label}"/></span>
