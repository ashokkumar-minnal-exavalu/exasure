package com.example;

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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

public class QuotePdfGenerator extends AbstractMediator {

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

            log.info("========== QUOTE PDF GENERATION START ==========");

            String jsonPayload =
                    extractJson(mc);

            log.info("INPUT PAYLOAD : " + jsonPayload);

            Map<String, Object> root =
                    MAPPER.readValue(
                            jsonPayload,
                            new TypeReference<Map<String, Object>>() {}
                    );

            log.info("ROOT OBJECT PARSED SUCCESSFULLY");

            byte[] pdf =
                    createPdf(root);

            log.info("PDF GENERATED. SIZE : " + pdf.length);

            String base64 =
                    Base64.getEncoder()
                            .encodeToString(pdf);

            log.info("BASE64 SIZE : " + base64.length());

            mc.setProperty(
                    "quoteBinaryData",
                    base64
            );

            log.info("Quote PDF generated successfully");

            log.info("========== QUOTE PDF GENERATION END ==========");

            return true;

        } catch (Exception e) {

            log.error(
                    "Quote PDF generation failed",
                    e
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
    // PDF CREATION
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

            drawQuoteSummary(
                    ctx,
                    root
            );

            renderPolicyInfo(
                    ctx,
                    root
            );

            renderRiskAttributes(
                    ctx,
                    root
            );

            renderIntermediary(
                    ctx,
                    root
            );

            renderDrivers(
                    ctx,
                    root
            );

            renderUnderwriting(
                    ctx,
                    root
            );

            renderVehicles(
                    ctx,
                    root
            );

            renderCargo(
                    ctx,
                    root
            );

            renderDocuments(
                    ctx,
                    root
            );

            renderPricing(
                    ctx,
                    root
            );

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
    ) throws Exception {

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

        ctx.cs.setNonStrokingColor(PRIMARY);

        ctx.cs.addRect(
                0,
                h - HEADER_HEIGHT,
                w,
                HEADER_HEIGHT
        );

        ctx.cs.fill();

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

        ctx.cs.showText(
                ctx.productCode + " Quote | Confidential"
        );

        ctx.cs.endText();

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
    ) throws Exception {

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
    ) throws Exception {

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

        writeKV(ctx.cs, "Quote ID",
                quote != null
                        ? getString(quote, "quoteId")
                        : "",
                left,
                row);

        writeKV(ctx.cs, "Quote Status",
                quote != null
                        ? getString(quote, "status")
                        : "",
                left,
                row - 16);

        writeKV(ctx.cs, "Quote Date",
                quote != null
                        ? getString(quote, "quoteIntimationDate")
                        : "",
                left,
                row - 32);

        writeKV(ctx.cs, "Product",
                getString(root, "productCode"),
                left,
                row - 48);

        writeKV(ctx.cs, "Channel",
                getString(root, "channel"),
                left,
                row - 64);

        Map<String, Object> party =
                getFirstMap(root, "parties");

        writeKV(ctx.cs, "Policy Holder",
                party != null
                        ? getString(party, "partyName")
                        : "",
                right,
                row);

        writeKV(ctx.cs, "Policy Period",
                getString(root, "policyPeriod"),
                right,
                row - 16);

        writeKV(ctx.cs, "Term Start",
                getString(root, "termStartDate"),
                right,
                row - 32);

        writeKV(ctx.cs, "Term End",
                getString(root, "termEndDate"),
                right,
                row - 48);

        writeKV(ctx.cs, "Duration",
                calculateDuration(
                        getString(root, "termStartDate"),
                        getString(root, "termEndDate")
                ),
                right,
                row - 64);

        ctx.y -= (boxH + 15);
    }

    // =========================================================
    // POLICY INFO
    // =========================================================

    private void renderPolicyInfo(
            Ctx ctx,
            Map<String, Object> root
    ) throws Exception {

        renderSection(
                ctx,
                "Policy Information"
        );

        writePair(ctx, "Product Code",
                getString(root, "productCode"));

        writePair(ctx, "Channel",
                getString(root, "channel"));

        writePair(ctx, "Policy Period",
                getString(root, "policyPeriod"));

        writePair(ctx, "Term Start",
                getString(root, "termStartDate"));

        writePair(ctx, "Term End",
                getString(root, "termEndDate"));

        ctx.y -= 6;
    }

    // =========================================================
    // RISK
    // =========================================================

    private void renderRiskAttributes(
            Ctx ctx,
            Map<String, Object> root
    ) throws Exception {

        Map<String, Object> risk =
                getMap(root, "riskAttributes");

        if (risk == null) {
            return;
        }

        renderSection(
                ctx,
                "Risk Attributes"
        );

        writePair(ctx, "Fleet Size",
                getString(risk, "fleetSize"));

        writePair(ctx, "Radius",
                getString(risk, "radiusOfOperation"));

        writePair(ctx, "Industry Code",
                getString(risk, "industryCode"));

        ctx.y -= 6;
    }

    // =========================================================
    // INTERMEDIARY
    // =========================================================

    private void renderIntermediary(
            Ctx ctx,
            Map<String, Object> root
    ) throws Exception {

        Map<String, Object> intermediary =
                getMap(root, "intermediary");

        if (intermediary == null) {
            return;
        }

        renderSection(
                ctx,
                "Intermediary"
        );

        writePair(ctx, "Party ID",
                getString(intermediary, "partyIdentifier"));

        writePair(ctx, "Type",
                getString(intermediary, "partyTypeCode"));

        writePair(ctx, "Role",
                toDisplayValue(
                        intermediary.get("roleTypeCode")
                ));

        ctx.y -= 6;
    }

    // =========================================================
    // DRIVERS
    // =========================================================

    private void renderDrivers(
            Ctx ctx,
            Map<String, Object> root
    ) throws Exception {

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

            ensureSpace(ctx, 120);

            writeSubheading(
                    ctx,
                    getString(d, "driverName")
            );

            writePair(ctx, "Role",
                    toDisplayValue(
                            d.get("roleTypeCode")
                    ));

            writePair(ctx, "Party ID",
                    getString(d, "partyIdentifier"));

            writePair(ctx, "License",
                    getString(d, "driverLicenseNumber"));

            writePair(ctx, "Jurisdiction",
                    getString(d, "driverLicenseJurisdictionCode"));

            writePair(ctx, "Experience",
                    getString(d, "drivingExperienceYearCount"));

            writePair(ctx, "CDL",
                    yesNo(d, "cdlIndicator"));

            writePair(ctx, "Accidents",
                    getString(d, "accidentCount"));

            writePair(ctx, "Violations",
                    getString(d, "violationCount"));

            ctx.y -= 10;
        }
    }

    // =========================================================
    // UNDERWRITING
    // =========================================================

    private void renderUnderwriting(
            Ctx ctx,
            Map<String, Object> root
    ) throws Exception {

        List<Map<String, Object>> uw =
                getList(root, "underWritingQuestions");

        if (uw.isEmpty()) {
            return;
        }

        renderSection(
                ctx,
                "Underwriting Questions"
        );

        for (Map<String, Object> q : uw) {

            for (Map.Entry<String, Object> e :
                    q.entrySet()) {

                writePair(
                        ctx,
                        camelToLabel(e.getKey()),
                        String.valueOf(e.getValue())
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
    ) throws Exception {

        List<Map<String, Object>> vehicles =
                getList(root, "insurableObjects");

        if (vehicles.isEmpty()) {
            return;
        }

        renderSection(
                ctx,
                "Vehicles"
        );

        int idx = 1;

        for (Map<String, Object> io : vehicles) {

            Map<String, Object> v =
                    getMap(io, "vehicle");

            if (v == null) {
                continue;
            }

            ensureSpace(ctx, 220);

            writeSubheading(
                    ctx,
                    "Vehicle " + idx++
            );

            writePair(ctx, "VIN",
                    getString(v, "vehicleIdentificationNumber"));

            writePair(ctx, "Make",
                    getString(v, "vehicleMakeName"));

            writePair(ctx, "Model",
                    getString(v, "vehicleModelName"));

            writePair(ctx, "Year",
                    getString(v, "vehicleModelYear"));

            writePair(ctx, "Body Type",
                    getString(v, "bodyType"));

            writePair(ctx, "Usage",
                    getString(v, "vehicleUsageCode"));

            writePair(ctx, "Plate",
                    getString(v, "licensePlateNumber"));

            writePair(ctx, "Value",
                    getString(v, "vehicleValue"));

            writePair(ctx, "Mileage",
                    getString(v, "annualMileage"));

            writePair(ctx, "Travel Radius",
                    getString(v, "travelRadiusFarm"));

            writePair(ctx, "Safety",
                    yesNo(v, "vehicleSafetyFeatureIndicator"));

            writePair(ctx, "Anti Theft",
                    yesNo(v, "vehicleAntiTheftIndicator"));

            writePair(ctx, "Damage",
                    yesNo(v, "unrepairedDamage"));

            List<Map<String, Object>> coverages =
                    getList(io, "coverages");

            if (!coverages.isEmpty()) {

                StringJoiner sj =
                        new StringJoiner(", ");

                for (Map<String, Object> c :
                        coverages) {

                    sj.add(
                            getString(c, "coverageName")
                    );
                }

                writePair(
                        ctx,
                        "Coverages",
                        sj.toString()
                );
            }

            List<Map<String, Object>> assigned =
                    getList(io, "assignedDrivers");

            if (!assigned.isEmpty()) {

                for (Map<String, Object> ad :
                        assigned) {

                    writePair(
                            ctx,
                            "Assigned Driver",
                            getString(ad, "partyIdentifier")
                    );
                }
            }

            ctx.y -= 10;
        }
    }

    // =========================================================
    // CARGO
    // =========================================================

    private void renderCargo(
            Ctx ctx,
            Map<String, Object> root
    ) throws Exception {

        Map<String, Object> cargo =
                getMap(root, "cargo");

        if (cargo == null) {
            return;
        }

        renderSection(
                ctx,
                "Cargo"
        );

        writePair(ctx, "Type",
                getString(cargo, "cargoTypeCode"));

        writePair(ctx, "Description",
                getString(cargo, "cargoDescription"));

        writePair(ctx, "Value",
                getString(cargo, "cargoValue"));

        writePair(ctx, "Currency",
                getString(cargo, "currencyCode"));

        writePair(ctx, "Manifest",
                getString(cargo, "cargoManifestNumber"));

        writePair(ctx, "Hazardous",
                yesNo(cargo, "hazardousMaterialIndicator"));

        writePair(ctx, "Temperature Controlled",
                yesNo(cargo, "temperatureControlled"));

        ctx.y -= 6;
    }

    // =========================================================
    // DOCUMENTS
    // =========================================================

    private void renderDocuments(
            Ctx ctx,
            Map<String, Object> root
    ) throws Exception {

        List<Map<String, Object>> docs =
                getList(root, "documents");

        if (docs.isEmpty()) {
            return;
        }

        renderSection(
                ctx,
                "Documents"
        );

        for (Map<String, Object> d : docs) {

            writePair(
                    ctx,
                    "Document Type",
                    getString(d, "documentTypeCode")
            );

            writePair(
                    ctx,
                    "Display Name",
                    getString(d, "displayName")
            );

            writePair(
                    ctx,
                    "Generated Date",
                    getString(d, "generatedDate")
            );

            writePair(
                    ctx,
                    "Type",
                    getString(d, "type")
            );

            ctx.y -= 6;
        }
    }

    // =========================================================
    // PRICING
    // =========================================================

    private void renderPricing(
            Ctx ctx,
            Map<String, Object> root
    ) throws Exception {

        renderSection(
                ctx,
                "Pricing"
        );

        writePair(
                ctx,
                "Total Premium",
                getStringPath(root, "pricing.totalPremium")
        );

        writePair(
                ctx,
                "Gross Fees",
                getStringPath(root, "pricing.grossFees")
        );

        List<Map<String, Object>> amounts =
                getList(
                        getMap(root, "pricing"),
                        "amounts"
                );

        for (Map<String, Object> a :
                amounts) {

            writePair(
                    ctx,
                    getString(a, "amountTypeCode"),
                    getString(a, "amountValue")
                            + " "
                            + getString(a, "currencyCode")
            );
        }

        ctx.y -= 6;
    }

    // =========================================================
    // SECTION
    // =========================================================

    private void renderSection(
            Ctx ctx,
            String title
    ) throws Exception {

        ensureSpace(
                ctx,
                50
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

        ctx.cs.showText(title);

        ctx.cs.endText();

        ctx.y -= SECTION_SPACING;
    }

    // =========================================================
    // FOOTERS
    // =========================================================

    private void drawFooters(
            Ctx ctx,
            Map<String, Object> root
    ) throws Exception {

        int total =
                ctx.doc.getNumberOfPages();

        for (int i = 0; i < total; i++) {

            PDPage pg =
                    ctx.doc.getPage(i);

            PDPageContentStream fcs =
                    new PDPageContentStream(
                            ctx.doc,
                            pg,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true
                    );

            float fw =
                    pg.getMediaBox().getWidth();

            float fy =
                    PAGE_MARGIN - 8;

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
                    "Page "
                            + (i + 1)
                            + " of "
                            + total
            );

            fcs.endText();

            fcs.close();
        }
    }

    // =========================================================
    // WRITERS
    // =========================================================

    private void writeSubheading(
            Ctx ctx,
            String text
    ) throws Exception {

        ensureSpace(ctx, 20);

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
    ) throws Exception {

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
                truncate(label + ": ", 35)
        );

        ctx.cs.endText();

        ctx.cs.beginText();

        ctx.cs.setFont(
                PDType1Font.HELVETICA,
                FONT_BODY
        );

        ctx.cs.setNonStrokingColor(TEXT);

        ctx.cs.newLineAtOffset(
                PAGE_MARGIN + 170,
                ctx.y
        );

        ctx.cs.showText(
                truncate(value, 85)
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
    ) throws Exception {

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

        cs.showText(k + ": ");

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
                truncate(v, 40)
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
                    ((Map<?, ?>) cur)
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

    private String yesNo(
            Map<String, Object> m,
            String key
    ) {

        Object v =
                m != null
                        ? m.get(key)
                        : null;

        if (v == null) {
            return "";
        }

        if (v instanceof Boolean) {

            return (Boolean) v
                    ? "Yes"
                    : "No";
        }

        String s =
                String.valueOf(v).trim();

        if ("true".equalsIgnoreCase(s)) {
            return "Yes";
        }

        if ("false".equalsIgnoreCase(s)) {
            return "No";
        }

        return s;
    }

    private String toDisplayValue(
            Object value
    ) {

        if (value == null) {
            return "";
        }

        if (value instanceof List) {

            StringJoiner sj =
                    new StringJoiner(", ");

            for (Object o :
                    (List<?>) value) {

                if (o != null) {

                    sj.add(
                            String.valueOf(o)
                    );
                }
            }

            return sj.toString();
        }

        return String.valueOf(value);
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
                );

        return Character.toUpperCase(
                spaced.charAt(0)
        ) + spaced.substring(1);
    }

    private String calculateDuration(
            String start,
            String end
    ) {

        try {

            LocalDate s =
                    LocalDate.parse(start);

            LocalDate e =
                    LocalDate.parse(end);

            int months =
                    (e.getYear() - s.getYear()) * 12
                    + e.getMonthValue()
                    - s.getMonthValue();

            return months + " months";

        } catch (DateTimeParseException ex) {

            return "";
        }
    }

    private String formatProductCode(
            String code
    ) {

        if (code == null) {
            return "";
        }

        return code.replace("_", " ");
    }
}