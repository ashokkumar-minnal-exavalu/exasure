package com.example.wso2.pdf;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

public class QuotePdfGeneratorMediator extends AbstractMediator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // =========================================================
    // COLORS
    // =========================================================

    private static final Color PRIMARY =
            new Color(0, 71, 133);

    private static final Color SECONDARY =
            new Color(0, 120, 200);

    private static final Color TEXT =
            new Color(40, 40, 40);

    private static final Color LIGHT_GRAY =
            new Color(245, 245, 245);

    private static final Color MUTED =
            new Color(120, 120, 120);

    // =========================================================
    // LAYOUT
    // =========================================================

    private static final float PAGE_MARGIN = 40f;

    private static final float HEADER_HEIGHT = 110f;

    private static final float FOOTER_HEIGHT = 30f;

    private static final float SECTION_BAR_H = 22f;

    private static final float LINE_SM = 14f;

    private static final float SECTION_SPACING = 35f;

    private static final float PAGE_BOTTOM_SAFE =
            PAGE_MARGIN + FOOTER_HEIGHT + 20f;

    // =========================================================
    // FONTS
    // =========================================================

    private static final int FONT_TITLE = 22;

    private static final int FONT_SUB = 10;

    private static final int FONT_SECTION = 11;

    private static final int FONT_BODY = 9;

    private static final int FONT_LABEL = 8;

    // =========================================================
    // CONTEXT
    // =========================================================

    private static final class Ctx {

        final PDDocument doc;

        PDPage page;

        PDPageContentStream cs;

        float y;

        int pageNum = 0;

        String productCode = "";

        Ctx(PDDocument doc) {
            this.doc = doc;
        }
    }

    // =========================================================
    // MEDIATOR
    // =========================================================

    @Override
    public boolean mediate(MessageContext mc) {

        try {

            String jsonPayload =
                    extractJson(mc);

            Map<String, Object> root =
                    MAPPER.readValue(
                            jsonPayload,
                            new TypeReference<Map<String, Object>>() {}
                    );

            byte[] pdf =
                    createPdf(root);

            String base64 =
                    Base64.getEncoder()
                            .encodeToString(pdf);

            mc.setProperty(
                    "quoteBinaryData",
                    base64
            );

            log.info("Quote PDF generated successfully");

            return true;

        } catch (Exception e) {

            log.error(
                    "Quote PDF generation failed",
                    e
            );

            mc.setProperty(
                    "ERROR_MESSAGE",
                    "Quote PDF generation failed"
            );

            mc.setProperty(
                    "ERROR_DETAIL",
                    e.getMessage()
            );

            return false;
        }
    }

    // =========================================================
    // JSON EXTRACTION
    // =========================================================

    private String extractJson(
            MessageContext mc
    ) throws Exception {

        Axis2MessageContext axis2MessageContext =
                (Axis2MessageContext) mc;

        org.apache.axis2.context.MessageContext axis2Ctx =
                axis2MessageContext.getAxis2MessageContext();

        if (!JsonUtil.hasAJsonPayload(axis2Ctx)) {

            throw new Exception(
                    "No JSON payload found"
            );
        }

        InputStream stream =
                JsonUtil.getJsonPayload(axis2Ctx);

        return new String(
                stream.readAllBytes(),
                StandardCharsets.UTF_8
        );
    }

    // =========================================================
    // PDF
    // =========================================================

    private byte[] createPdf(
            Map<String, Object> root
    ) throws Exception {

        try (
                PDDocument doc =
                        new PDDocument();

                ByteArrayOutputStream baos =
                        new ByteArrayOutputStream()
        ) {

            Ctx ctx =
                    new Ctx(doc);

            ctx.productCode =
                    formatProductCode(
                            getString(root, "productCode")
                    );

            newPage(ctx);

            // =====================================================
            // SUMMARY
            // =====================================================

            drawQuoteSummary(
                    ctx,
                    root
            );

            // =====================================================
            // RISK ATTRIBUTES
            // =====================================================

            Map<String, Object> risk =
                    getMap(root, "riskAttributes");

            if (risk != null) {

                renderSection(
                        ctx,
                        "Risk Attributes"
                );

                writePair(
                        ctx,
                        "Fleet Size",
                        getString(risk, "fleetSize")
                );

                writePair(
                        ctx,
                        "Radius Of Operation",
                        getString(risk, "radiusOfOperation")
                );

                writePair(
                        ctx,
                        "Industry Code",
                        getString(risk, "industryCode")
                );

                ctx.y -= 6;
            }

            // =====================================================
            // INTERMEDIARY
            // =====================================================

            Map<String, Object> intermediary =
                    getMap(root, "intermediary");

            if (intermediary != null) {

                renderSection(
                        ctx,
                        "Intermediary / Agent"
                );

                writePair(
                        ctx,
                        "Party ID",
                        getString(intermediary, "partyIdentifier")
                );

                writePair(
                        ctx,
                        "Type",
                        getString(intermediary, "partyTypeCode")
                );

                writePair(
                        ctx,
                        "Role",
                        getString(intermediary, "roleTypeCode")
                );

                writePair(
                        ctx,
                        "Commission Eligible",
                        Boolean.TRUE.equals(
                                intermediary.get(
                                        "commissionEligibleIndicator"
                                )
                        )
                                ? "Yes"
                                : "No"
                );

                ctx.y -= 6;
            }

            // =====================================================
            // DRIVERS
            // =====================================================

            renderDrivers(
                    ctx,
                    root
            );

            // =====================================================
            // UNDERWRITING
            // =====================================================

            renderUnderwriting(
                    ctx,
                    root
            );

            // =====================================================
            // VEHICLES
            // =====================================================

            renderVehicles(
                    ctx,
                    root
            );

            // =====================================================
            // CARGO
            // =====================================================

            renderCargo(
                    ctx,
                    root
            );

            // =====================================================
            // PRICING
            // =====================================================

            renderPricing(
                    ctx,
                    root
            );

            // =====================================================
            // FOOTERS
            // =====================================================

            drawFooters(
                    ctx,
                    root
            );

            if (ctx.cs != null) {
                ctx.cs.close();
            }

            doc.save(baos);

            return baos.toByteArray();
        }
    }

    // =========================================================
    // PAGE MANAGEMENT
    // =========================================================

    private void newPage(
            Ctx ctx
    ) throws IOException {

        if (ctx.cs != null) {
            ctx.cs.close();
        }

        ctx.page =
                new PDPage(PDRectangle.LETTER);

        ctx.doc.addPage(ctx.page);

        ctx.pageNum++;

        ctx.cs =
                new PDPageContentStream(
                        ctx.doc,
                        ctx.page
                );

        float h =
                ctx.page.getMediaBox().getHeight();

        float w =
                ctx.page.getMediaBox().getWidth();

        // HEADER

        ctx.cs.setNonStrokingColor(PRIMARY);

        ctx.cs.addRect(
                0,
                h - HEADER_HEIGHT,
                w,
                HEADER_HEIGHT
        );

        ctx.cs.fill();

        // TITLE

        ctx.cs.beginText();

        ctx.cs.setFont(
                PDType1Font.HELVETICA_BOLD,
                FONT_TITLE
        );

        ctx.cs.setNonStrokingColor(Color.WHITE);

        ctx.cs.newLineAtOffset(
                PAGE_MARGIN,
                h - 48
        );

        ctx.cs.showText(
                "EXAVALU INSURANCE"
        );

        ctx.cs.endText();

        // SUBTITLE

        ctx.cs.beginText();

        ctx.cs.setFont(
                PDType1Font.HELVETICA,
                FONT_SUB
        );

        ctx.cs.setNonStrokingColor(Color.WHITE);

        ctx.cs.newLineAtOffset(
                PAGE_MARGIN,
                h - 66
        );

        String subtitle =
                ctx.productCode.isEmpty()
                        ? "Quote | Confidential"
                        : ctx.productCode + " Quote | Confidential";

        ctx.cs.showText(
                subtitle
        );

        ctx.cs.endText();

        // DATE

        String today =
                new SimpleDateFormat(
                        "MMMM dd, yyyy"
                ).format(new Date());

        ctx.cs.beginText();

        ctx.cs.setFont(
                PDType1Font.HELVETICA,
                FONT_SUB
        );

        ctx.cs.setNonStrokingColor(Color.WHITE);

        ctx.cs.newLineAtOffset(
                w - PAGE_MARGIN - 160,
                h - 66
        );

        ctx.cs.showText(
                "Generated: " + today
        );

        ctx.cs.endText();

        ctx.y =
                h - HEADER_HEIGHT - 20;
    }

    private void ensureSpace(
            Ctx ctx,
            float needed
    ) throws IOException {

        if (ctx.y - needed < PAGE_BOTTOM_SAFE) {

            newPage(ctx);
        }
    }

    // =========================================================
    // SUMMARY
    // =========================================================

    private void drawQuoteSummary(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

        float w =
                ctx.page.getMediaBox().getWidth();

        float boxH = 110f;

        ctx.cs.setNonStrokingColor(LIGHT_GRAY);

        ctx.cs.addRect(
                PAGE_MARGIN,
                ctx.y - boxH,
                w - 2 * PAGE_MARGIN,
                boxH
        );

        ctx.cs.fill();

        float left =
                PAGE_MARGIN + 15;

        float right =
                PAGE_MARGIN + (w - 2 * PAGE_MARGIN) / 2 + 10;

        float row =
                ctx.y - 18;

        Map<String, Object> quote =
                getMap(root, "quote");

        writeKV(
                ctx.cs,
                "Quote ID",
                quote != null
                        ? getString(quote, "quoteId")
                        : "",
                left,
                row
        );

        writeKV(
                ctx.cs,
                "Quote Status",
                quote != null
                        ? getString(quote, "status")
                        : "",
                left,
                row - 16
        );

        writeKV(
                ctx.cs,
                "Intimation Date",
                quote != null
                        ? getString(
                                quote,
                                "quoteIntimationDate"
                        )
                        : "",
                left,
                row - 32
        );

        writeKV(
                ctx.cs,
                "Product",
                getString(root, "productCode"),
                left,
                row - 48
        );

        writeKV(
                ctx.cs,
                "Channel",
                getString(root, "channel"),
                left,
                row - 64
        );

        Map<String, Object> party =
                getFirstMap(root, "parties");

        writeKV(
                ctx.cs,
                "Policy Holder",
                party != null
                        ? getString(party, "partyName")
                        + " ("
                        + getString(party, "partyIdentifier")
                        + ")"
                        : "",
                right,
                row
        );

        writeKV(
                ctx.cs,
                "Policy Period",
                getString(root, "policyPeriod"),
                right,
                row - 16
        );

        writeKV(
                ctx.cs,
                "Term Start",
                getString(root, "termStartDate"),
                right,
                row - 32
        );

        writeKV(
                ctx.cs,
                "Term End",
                getString(root, "termEndDate"),
                right,
                row - 48
        );

        writeKV(
                ctx.cs,
                "Duration",
                calculateDuration(
                        getString(root, "termStartDate"),
                        getString(root, "termEndDate")
                ),
                right,
                row - 64
        );

        ctx.y -= (boxH + 15);
    }

    // =========================================================
    // SECTION
    // =========================================================

    private void renderSection(
            Ctx ctx,
            String title
    ) throws IOException {

        ensureSpace(
                ctx,
                SECTION_SPACING + 20
        );

        float w =
                ctx.page.getMediaBox().getWidth();

        ctx.cs.setNonStrokingColor(PRIMARY);

        ctx.cs.addRect(
                PAGE_MARGIN,
                ctx.y - SECTION_BAR_H,
                w - 2 * PAGE_MARGIN,
                SECTION_BAR_H
        );

        ctx.cs.fill();

        ctx.cs.beginText();

        ctx.cs.setFont(
                PDType1Font.HELVETICA_BOLD,
                FONT_SECTION
        );

        ctx.cs.setNonStrokingColor(Color.WHITE);

        ctx.cs.newLineAtOffset(
                PAGE_MARGIN + 10,
                ctx.y - 15
        );

        ctx.cs.showText(
                title
        );

        ctx.cs.endText();

        ctx.y -= SECTION_SPACING;
    }

    // =========================================================
    // DRIVERS
    // =========================================================

    private void renderDrivers(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

        List<Map<String, Object>> drivers =
                getList(root, "driverDetails");

        if (drivers.isEmpty()) {
            return;
        }

        renderSection(
                ctx,
                "Drivers"
        );

        for (Map<String, Object> d : drivers) {

            ensureSpace(ctx, 90);

            writeSubheading(
                    ctx,
                    getString(d, "driverName")
                    + " ["
                    + getString(d, "roleTypeCode")
                    + "]"
            );

            writePair(
                    ctx,
                    "Party ID",
                    getString(d, "partyIdentifier")
            );

            writePair(
                    ctx,
                    "License #",
                    getString(
                            d,
                            "driverLicenseNumber"
                    )
            );

            writePair(
                    ctx,
                    "Jurisdiction",
                    getString(
                            d,
                            "driverLicenseJurisdictionCode"
                    )
                    + " / "
                    + getString(
                            d,
                            "driverLicenseJurisdictionZipCode"
                    )
            );

            writePair(
                    ctx,
                    "Experience (yrs)",
                    getString(
                            d,
                            "drivingExperienceYearCount"
                    )
            );

            writePair(
                    ctx,
                    "Licensed (yrs)",
                    getString(
                            d,
                            "yearsLicensedCount"
                    )
            );

            writePair(
                    ctx,
                    "CDL",
                    Boolean.TRUE.equals(
                            d.get("cdlIndicator")
                    )
                            ? "Yes"
                            : "No"
            );

            writePair(
                    ctx,
                    "Accidents / Violations",
                    getString(d, "accidentCount")
                    + " / "
                    + getString(d, "violationCount")
            );

            ctx.y -= 8;
        }
    }

    // =========================================================
    // UNDERWRITING
    // =========================================================

    private void renderUnderwriting(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

        List<Map<String, Object>> uwQs =
                getList(
                        root,
                        "underWritingQuestions"
                );

        if (uwQs.isEmpty()) {
            return;
        }

        renderSection(
                ctx,
                "Underwriting Questions"
        );

        for (Map<String, Object> q : uwQs) {

            for (Map.Entry<String, Object> e :
                    q.entrySet()) {

                writePair(
                        ctx,
                        camelToLabel(
                                e.getKey()
                        ),
                        String.valueOf(
                                e.getValue()
                        )
                );
            }
        }

        ctx.y -= 6;
    }

    // =========================================================
    // VEHICLES
    // =========================================================

    private void renderVehicles(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

        List<Map<String, Object>> objects =
                getList(
                        root,
                        "insurableObjects"
                );

        if (objects.isEmpty()) {
            return;
        }

        renderSection(
                ctx,
                "Vehicles"
        );

        int idx = 1;

        for (Map<String, Object> io : objects) {

            Map<String, Object> v =
                    getMap(io, "vehicle");

            if (v == null) {
                continue;
            }

            ensureSpace(ctx, 140);

            writeSubheading(
                    ctx,
                    "Vehicle "
                    + idx++
                    + ": "
                    + getString(v, "vehicleModelYear")
                    + " "
                    + getString(v, "vehicleMakeName")
                    + " "
                    + getString(v, "vehicleModelName")
            );

            writePair(
                    ctx,
                    "VIN",
                    getString(
                            v,
                            "vehicleIdentificationNumber"
                    )
            );

            writePair(
                    ctx,
                    "Body Type",
                    getString(v, "bodyType")
            );

            writePair(
                    ctx,
                    "Type / Usage",
                    getString(v, "vehicleTypeCode")
                    + " / "
                    + getString(v, "vehicleUsageCode")
            );

            writePair(
                    ctx,
                    "License Plate",
                    getString(v, "licensePlateNumber")
                    + " ("
                    + getString(v, "licenseState")
                    + ")"
            );

            writePair(
                    ctx,
                    "Ownership",
                    getString(v, "ownershipTypeCode")
            );

            writePair(
                    ctx,
                    "Value",
                    fmt(
                            getString(
                                    v,
                                    "vehicleValue"
                            )
                    )
            );

            writePair(
                    ctx,
                    "Annual Mileage",
                    getString(v, "annualMileage")
            );

            writePair(
                    ctx,
                    "GVW",
                    getString(v, "grossVehicleWeight")
            );

            writePair(
                    ctx,
                    "CC",
                    getString(v, "cc")
            );

            writePair(
                    ctx,
                    "Travel Radius",
                    getString(v, "travelRadiusFarm")
                    + " mi"
            );

            writePair(
                    ctx,
                    "Garaging ZIP",
                    getString(
                            v,
                            "vehicleGaragingPostalCode"
                    )
            );

            writePair(
                    ctx,
                    "Safety / Anti-Theft",
                    yesNo(
                            v,
                            "vehicleSafetyFeatureIndicator"
                    )
                    + " / "
                    + yesNo(
                            v,
                            "vehicleAntiTheftIndicator"
                    )
            );

            writePair(
                    ctx,
                    "Unrepaired Damage",
                    yesNo(v, "unrepairedDamage")
            );

            // ASSIGNED DRIVERS

            List<Map<String, Object>> aDrivers =
                    getList(io, "assignedDrivers");

            if (!aDrivers.isEmpty()) {

                writeLabelOnly(
                        ctx,
                        "Assigned Drivers:"
                );

                for (Map<String, Object> ad : aDrivers) {

                    writePair(
                            ctx,
                            "Party ID",
                            getString(
                                    ad,
                                    "partyIdentifier"
                            )
                            + " License: "
                            + getString(
                                    ad,
                                    "driverLicenseNumber"
                            )
                    );
                }
            }

            // COVERAGES

            List<Map<String, Object>> coverages =
                    getList(io, "coverages");

            if (!coverages.isEmpty()) {

                StringJoiner sj =
                        new StringJoiner(", ");

                for (Map<String, Object> c :
                        coverages) {

                    String name =
                            getString(
                                    c,
                                    "coverageName"
                            );

                    if (!name.isBlank()) {

                        sj.add(name);
                    }
                }

                writePair(
                        ctx,
                        "Coverage Tier",
                        sj.toString()
                );
            }

            ctx.y -= 8;
        }
    }

    // =========================================================
    // CARGO
    // =========================================================

    private void renderCargo(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

        Map<String, Object> cargo =
                getMap(root, "cargo");

        if (cargo == null) {
            return;
        }

        renderSection(
                ctx,
                "Cargo Details"
        );

        writePair(
                ctx,
                "Cargo ID",
                getString(cargo, "cargoIdentifier")
        );

        writePair(
                ctx,
                "Type",
                getString(cargo, "cargoTypeCode")
        );

        writePair(
                ctx,
                "Description",
                getString(cargo, "cargoDescription")
        );

        writePair(
                ctx,
                "Value",
                fmt(
                        getString(cargo, "cargoValue")
                )
                + " "
                + getString(cargo, "currencyCode")
        );

        writePair(
                ctx,
                "Manifest #",
                getString(
                        cargo,
                        "cargoManifestNumber"
                )
        );

        writePair(
                ctx,
                "Hazardous",
                yesNo(
                        cargo,
                        "hazardousMaterialIndicator"
                )
        );

        writePair(
                ctx,
                "Temp Controlled",
                yesNo(
                        cargo,
                        "temperatureControlled"
                )
        );

        ctx.y -= 6;
    }

    // =========================================================
    // PRICING
    // =========================================================

    private void renderPricing(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

        renderSection(
                ctx,
                "Pricing"
        );

        String currency =
                resolveCurrency(root);

        writePair(
                ctx,
                "Total Premium",
                fmt(
                        getStringPath(
                                root,
                                "pricing.totalPremium"
                        )
                )
                + " "
                + currency
        );

        writePair(
                ctx,
                "Gross Fees",
                fmt(
                        getStringPath(
                                root,
                                "pricing.grossFees"
                        )
                )
                + " "
                + currency
        );

        List<Map<String, Object>> amounts =
                getList(
                        getMap(root, "pricing") != null
                                ? getMap(root, "pricing")
                                : Collections.emptyMap(),
                        "amounts"
                );

        for (Map<String, Object> a : amounts) {

            writePair(
                    ctx,
                    getString(
                            a,
                            "amountTypeCode"
                    ),
                    fmt(
                            getString(
                                    a,
                                    "amountValue"
                            )
                    )
                    + " "
                    + getString(
                            a,
                            "currencyCode"
                    )
            );
        }

        ctx.y -= 6;
    }

    // =========================================================
    // FOOTERS
    // =========================================================

    private void drawFooters(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

        int total =
                ctx.doc.getNumberOfPages();

        Map<String, Object> quote =
                getMap(root, "quote");

        String quoteId =
                quote != null
                        ? getString(
                                quote,
                                "quoteId"
                        )
                        : "";

        for (int i = 0; i < total; i++) {

            PDPage pg =
                    ctx.doc.getPage(i);

            try (
                    PDPageContentStream fcs =
                            new PDPageContentStream(
                                    ctx.doc,
                                    pg,
                                    PDPageContentStream.AppendMode.APPEND,
                                    true,
                                    true
                            )
            ) {

                float fw =
                        pg.getMediaBox().getWidth();

                float fy =
                        PAGE_MARGIN - 8;

                fcs.setNonStrokingColor(MUTED);

                fcs.addRect(
                        PAGE_MARGIN,
                        fy - 2,
                        fw - 2 * PAGE_MARGIN,
                        0.5f
                );

                fcs.fill();

                fcs.beginText();

                fcs.setFont(
                        PDType1Font.HELVETICA,
                        7
                );

                fcs.setNonStrokingColor(MUTED);

                fcs.newLineAtOffset(
                        PAGE_MARGIN,
                        fy - 12
                );

                fcs.showText(
                        "Quote: "
                        + quoteId
                        + " | CONFIDENTIAL"
                );

                fcs.endText();

                fcs.beginText();

                fcs.setFont(
                        PDType1Font.HELVETICA,
                        7
                );

                fcs.setNonStrokingColor(MUTED);

                fcs.newLineAtOffset(
                        fw - PAGE_MARGIN - 50,
                        fy - 12
                );

                fcs.showText(
                        "Page "
                        + (i + 1)
                        + " of "
                        + total
                );

                fcs.endText();
            }
        }
    }

    // =========================================================
    // WRITERS
    // =========================================================

    private void writeSubheading(
            Ctx ctx,
            String text
    ) throws IOException {

        ensureSpace(ctx, LINE_SM + 4);

        ctx.cs.beginText();

        ctx.cs.setFont(
                PDType1Font.HELVETICA_BOLD,
                FONT_BODY + 1
        );

        ctx.cs.setNonStrokingColor(SECONDARY);

        ctx.cs.newLineAtOffset(
                PAGE_MARGIN + 10,
                ctx.y
        );

        ctx.cs.showText(
                truncate(text, 90)
        );

        ctx.cs.endText();

        ctx.y -= LINE_SM + 2;
    }

    private void writePair(
            Ctx ctx,
            String label,
            String value
    ) throws IOException {

        if (value == null || value.isBlank()) {
            return;
        }

        ensureSpace(ctx, LINE_SM);

        ctx.cs.beginText();

        ctx.cs.setFont(
                PDType1Font.HELVETICA_BOLD,
                FONT_LABEL
        );

        ctx.cs.setNonStrokingColor(MUTED);

        ctx.cs.newLineAtOffset(
                PAGE_MARGIN + 15,
                ctx.y
        );

        ctx.cs.showText(
                truncate(label + ": ", 30)
        );

        ctx.cs.endText();

        ctx.cs.beginText();

        ctx.cs.setFont(
                PDType1Font.HELVETICA,
                FONT_BODY
        );

        ctx.cs.setNonStrokingColor(TEXT);

        ctx.cs.newLineAtOffset(
                PAGE_MARGIN + 145,
                ctx.y
        );

        ctx.cs.showText(
                truncate(
                        safe(value),
                        70
                )
        );

        ctx.cs.endText();

        ctx.y -= LINE_SM;
    }

    private void writeLabelOnly(
            Ctx ctx,
            String text
    ) throws IOException {

        ensureSpace(ctx, LINE_SM);

        ctx.cs.beginText();

        ctx.cs.setFont(
                PDType1Font.HELVETICA_BOLD,
                FONT_BODY
        );

        ctx.cs.setNonStrokingColor(TEXT);

        ctx.cs.newLineAtOffset(
                PAGE_MARGIN + 15,
                ctx.y
        );

        ctx.cs.showText(
                truncate(text, 80)
        );

        ctx.cs.endText();

        ctx.y -= LINE_SM;
    }

    private void writeKV(
            PDPageContentStream cs,
            String k,
            String v,
            float x,
            float y
    ) throws IOException {

        if (v == null || v.isBlank()) {
            return;
        }

        cs.beginText();

        cs.setFont(
                PDType1Font.HELVETICA_BOLD,
                FONT_LABEL
        );

        cs.setNonStrokingColor(MUTED);

        cs.newLineAtOffset(x, y);

        cs.showText(
                truncate(k + ": ", 28)
        );

        cs.endText();

        cs.beginText();

        cs.setFont(
                PDType1Font.HELVETICA,
                FONT_BODY
        );

        cs.setNonStrokingColor(TEXT);

        cs.newLineAtOffset(
                x + 100,
                y
        );

        cs.showText(
                truncate(
                        safe(v),
                        40
                )
        );

        cs.endText();
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String getString(
            Map<String, Object> m,
            String k
    ) {

        Object v =
                m != null
                        ? m.get(k)
                        : null;

        return v != null
                ? String.valueOf(v)
                : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(
            Map<String, Object> m,
            String k
    ) {

        Object v =
                m != null
                        ? m.get(k)
                        : null;

        return v instanceof Map
                ? (Map<String, Object>) v
                : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(
            Map<String, Object> m,
            String k
    ) {

        Object v =
                m != null
                        ? m.get(k)
                        : null;

        if (!(v instanceof List)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (Object o : (List<?>) v) {

            if (o instanceof Map) {

                result.add(
                        (Map<String, Object>) o
                );
            }
        }

        return result;
    }

    private Map<String, Object> getFirstMap(
            Map<String, Object> m,
            String k
    ) {

        List<Map<String, Object>> list =
                getList(m, k);

        return list.isEmpty()
                ? null
                : list.get(0);
    }

    @SuppressWarnings("unchecked")
    private Object getByPath(
            Map<String, Object> m,
            String path
    ) {

        Object cur = m;

        for (String seg :
                path.split("\\.")) {

            if (!(cur instanceof Map)) {
                return null;
            }

            cur =
                    ((Map<String, Object>) cur)
                            .get(seg);
        }

        return cur;
    }

    private String getStringPath(
            Map<String, Object> m,
            String path
    ) {

        Object v =
                getByPath(m, path);

        return v != null
                ? String.valueOf(v)
                : "";
    }

    @SuppressWarnings("unchecked")
    private String resolveCurrency(
            Map<String, Object> root
    ) {

        Object a =
                getByPath(
                        root,
                        "pricing.amounts"
                );

        if (a instanceof List) {

            for (Object o : (List<?>) a) {

                if (o instanceof Map) {

                    Map<String, Object> am =
                            (Map<String, Object>) o;

                    if ("PREMIUM".equals(
                            getString(
                                    am,
                                    "amountTypeCode"
                            )
                    )) {

                        return getString(
                                am,
                                "currencyCode"
                        );
                    }
                }
            }
        }

        return "";
    }

    private String safe(
            String s
    ) {

        return s != null
                ? s
                : "";
    }

    private String truncate(
            String s,
            int max
    ) {

        if (s == null) {
            return "";
        }

        return s.length() <= max
                ? s
                : s.substring(0, max - 1) + "...";
    }

    private String yesNo(
            Map<String, Object> m,
            String key
    ) {

        Object v =
                m != null
                        ? m.get(key)
                        : null;

        if (v instanceof Boolean) {

            return (Boolean) v
                    ? "Yes"
                    : "No";
        }

        return safe(
                String.valueOf(v)
        );
    }

    private String fmt(
            String s
    ) {

        if (s == null || s.isBlank()) {
            return "";
        }

        try {

            long val =
                    Long.parseLong(
                            s.trim()
                    );

            return String.format(
                    "%,d",
                    val
            );

        } catch (NumberFormatException ignored) {

            return s;
        }
    }

    private String camelToLabel(
            String camel
    ) {

        if (camel == null || camel.isBlank()) {
            return camel;
        }

        String spaced =
                camel.replaceAll(
                        "([A-Z])",
                        " $1"
                )
                .replaceAll(
                        "(\\d+)",
                        " $1 "
                )
                .trim();

        return Character.toUpperCase(
                spaced.charAt(0)
        ) + spaced.substring(1);
    }

    private String calculateDuration(
            String start,
            String end
    ) {

        if (start == null
                || end == null
                || start.isBlank()
                || end.isBlank()) {

            return "";
        }

        try {

            LocalDate s =
                    LocalDate.parse(start);

            LocalDate e =
                    LocalDate.parse(end);

            int months =
                    (e.getYear() - s.getYear()) * 12
                    + e.getMonthValue()
                    - s.getMonthValue();

            if (months <= 0) {
                return "";
            }

            return months >= 12
                    ? (months / 12) + " year(s)"
                    : months + " month(s)";

        } catch (DateTimeParseException ex) {

            return "";
        }
    }

    private String formatProductCode(
            String code
    ) {

        if (code == null || code.isBlank()) {
            return "";
        }

        String[] parts =
                code.split("_");

        StringJoiner sj =
                new StringJoiner(" ");

        for (String part : parts) {

            if (part.isEmpty()) {
                continue;
            }

            sj.add(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
                    + part.substring(1)
                            .toLowerCase()
            );
        }

        return sj.toString();
    }
}