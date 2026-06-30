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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
    
public class PolicyPDFGenerator extends AbstractMediator { 

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

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

    private static final float PAGE_MARGIN = 40f;

    private static final float HEADER_HEIGHT = 100f;

    private static final float FOOTER_HEIGHT = 30f;

    private static final float SECTION_HEIGHT = 22f;

    private static final float LINE_HEIGHT = 14f;

    private static final float PAGE_BOTTOM =
            PAGE_MARGIN + FOOTER_HEIGHT + 20f;

    private static final int FONT_TITLE = 22;

    private static final int FONT_SUBTITLE = 10;

    private static final int FONT_SECTION = 11;

    private static final int FONT_BODY = 9;

    private static final int FONT_LABEL = 8;

    private static final class Ctx {

        final PDDocument doc;

        PDPage page;

        PDPageContentStream cs;

        float y;

        int pageNo = 0;

        String productCode = "";

        Ctx(PDDocument doc) {
            this.doc = doc;
        }
    }

    @Override
    public boolean mediate(MessageContext context) {

        try {

            log.info("========== PDF GENERATION START ==========");
            System.out.println("========== PDF GENERATION START ==========");

            String json =
                    extractRawJson(context);

            log.info("INPUT JSON : " + json);
            System.out.println("INPUT JSON : " + json);

            Map<String, Object> root =
                    MAPPER.readValue(
                            json,
                            new TypeReference<Map<String, Object>>() {}
                    );

            byte[] pdf =
                    generatePdf(root);

            log.info("FINAL PDF BYTE SIZE : " + pdf.length);
            System.out.println("FINAL PDF BYTE SIZE : " + pdf.length);

            String base64 =
                    Base64.getEncoder()
                            .encodeToString(pdf);

            context.setProperty(
                    "policyBinaryData",
                    base64
            );

            log.info("PDF GENERATED SUCCESSFULLY");
            System.out.println("PDF GENERATED SUCCESSFULLY");

            return true;

        } catch (Exception e) {

            log.error(
                    "PDF GENERATION FAILED",
                    e
            );

            e.printStackTrace();

            context.setProperty(
                    "ERROR_MESSAGE",
                    "PDF generation failed"
            );

            context.setProperty(
                    "ERROR_DETAIL",
                    e.getMessage()
            );

            return false;
        }
    }

    private String extractRawJson(
            MessageContext context
    ) throws Exception {

        Axis2MessageContext axis2MessageContext =
                (Axis2MessageContext) context;

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

    private byte[] generatePdf(
            Map<String, Object> root
    ) throws Exception {

        try (
                PDDocument doc = new PDDocument();
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

            try {

                log.info("Rendering Policy Summary");
                drawPolicySummary(ctx, root);

            } catch (Exception e) {

                log.error(
                        "FAILED : Policy Summary",
                        e
                );
            }

            try {

                log.info("Rendering Quote");

                Map<String, Object> quote =
                        getMap(root, "quote");

                if (quote != null) {

                    renderSection(
                            ctx,
                            "Quote Details"
                    );

                    renderDynamicFields(
                            ctx,
                            quote
                    );
                }

            } catch (Exception e) {

                log.error(
                        "FAILED : Quote Section",
                        e
                );
            }

            try {

                log.info("Rendering Risk Attributes");

                Map<String, Object> risk =
                        getMap(root, "riskAttributes");

                if (risk != null) {

                    renderSection(
                            ctx,
                            "Risk Attributes"
                    );

                    renderDynamicFields(
                            ctx,
                            risk
                    );
                }

            } catch (Exception e) {

                log.error(
                        "FAILED : Risk Attributes",
                        e
                );
            }

            try {

                log.info("Rendering Intermediary");

                Map<String, Object> intermediary =
                        getMap(root, "intermediary");

                if (intermediary != null) {

                    renderSection(
                            ctx,
                            "Intermediary"
                    );

                    renderDynamicFields(
                            ctx,
                            intermediary
                    );
                }

            } catch (Exception e) {

                log.error(
                        "FAILED : Intermediary",
                        e
                );
            }

            try {

                log.info("Rendering Parties");

                List<Map<String, Object>> parties =
                        getList(root, "parties");

                if (!parties.isEmpty()) {

                    renderSection(
                            ctx,
                            "Parties"
                    );

                    for (Map<String, Object> p : parties) {

                        renderDynamicFields(
                                ctx,
                                p
                        );

                        ctx.y -= 8;
                    }
                }

            } catch (Exception e) {

                log.error(
                        "FAILED : Parties",
                        e
                );
            }

            try {

                log.info("Rendering Drivers");

                renderDrivers(ctx, root);

            } catch (Exception e) {

                log.error(
                        "FAILED : Drivers",
                        e
                );
            }

            try {

                log.info("Rendering Vehicles");

                renderVehicles(ctx, root);

            } catch (Exception e) {

                log.error(
                        "FAILED : Vehicles",
                        e
                );
            }

            try {

                log.info("Rendering Cargo");

                renderCargo(ctx, root);

            } catch (Exception e) {

                log.error(
                        "FAILED : Cargo",
                        e
                );
            }

            try {

                log.info("Rendering Underwriting");

                renderUnderwriting(ctx, root);

            } catch (Exception e) {

                log.error(
                        "FAILED : Underwriting",
                        e
                );
            }

            try {

                log.info("Rendering Pricing");

                renderPricing(ctx, root);

            } catch (Exception e) {

                log.error(
                        "FAILED : Pricing",
                        e
                );
            }

            try {

                log.info("Rendering Transaction");

                renderTransaction(ctx, root);

            } catch (Exception e) {

                log.error(
                        "FAILED : Transaction",
                        e
                );
            }
            try {

                log.info("Rendering Policy Lifecycle");

                addPolicyLifecycle(ctx, root);

                } catch (Exception e) {

                log.error(
                        "FAILED : Policy Lifecycle",
                        e
                );
            }


            if (ctx.cs != null) {

                ctx.cs.close();
            }

            drawFooters(ctx, root);

            doc.save(baos);

            log.info("PDF SIZE : " + baos.size());
            System.out.println("PDF SIZE : " + baos.size());

            return baos.toByteArray();
        }
    }

    private void newPage(
            Ctx ctx
    ) throws IOException {

        log.info(
                "Creating New Page : "
                        + (ctx.pageNo + 1)
        );

        System.out.println(
                "Creating New Page : "
                        + (ctx.pageNo + 1)
        );

        if (ctx.cs != null) {

            ctx.cs.close();
        }

        ctx.page =
                new PDPage(PDRectangle.LETTER);

        ctx.doc.addPage(ctx.page);

        ctx.pageNo++;

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
                FONT_SUBTITLE
        );

        ctx.cs.setNonStrokingColor(Color.WHITE);

        ctx.cs.newLineAtOffset(
                PAGE_MARGIN,
                h - 66
        );

        ctx.cs.showText(
                ctx.productCode
        );

        ctx.cs.endText();

        ctx.y =
                h - HEADER_HEIGHT - 20;
    }

    private void ensureSpace(
            Ctx ctx,
            float needed
    ) throws IOException {

        if (ctx.y - needed < PAGE_BOTTOM) {

            newPage(ctx);
        }
    }

    private void renderSection(
            Ctx ctx,
            String title
    ) throws IOException {

        ensureSpace(ctx, 40);

        float w =
                ctx.page.getMediaBox().getWidth();

        ctx.cs.setNonStrokingColor(PRIMARY);

        ctx.cs.addRect(
                PAGE_MARGIN,
                ctx.y - SECTION_HEIGHT,
                w - 2 * PAGE_MARGIN,
                SECTION_HEIGHT
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

        ctx.y -= 35;
    }

    private void drawPolicySummary(
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

        writeKV(
                ctx.cs,
                "Policy Number",
                getString(root, "policyNumber"),
                left,
                row
        );

        writeKV(
                ctx.cs,
                "Status",
                getString(root, "policyStatusCode"),
                left,
                row - 16
        );

        writeKV(
                ctx.cs,
                "Product",
                getString(root, "productCode"),
                left,
                row - 32
        );

        writeKV(
                ctx.cs,
                "Policy Period",
                getString(root, "policyPeriod"),
                left,
                row - 48
        );

        writeKV(
                ctx.cs,
                "Policy Accept Date",
                getString(root, "policyAcceptDate"),
                right,
                row
        );

        writeKV(
                ctx.cs,
                "Channel",
                getString(root, "channel"),
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

        ctx.y -= (boxH + 15);
    }

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

            writeSubHeading(
                    ctx,
                    getString(d, "driverName")
            );

            renderDynamicFields(
                    ctx,
                    d
            );

            ctx.y -= 8;
        }
    }

    private void renderVehicles(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

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

            writeSubHeading(
                    ctx,
                    "Vehicle " + idx++
            );

            renderDynamicFields(
                    ctx,
                    v
            );

            List<Map<String, Object>> ads =
                    getList(io, "assignedDrivers");

            for (Map<String, Object> ad : ads) {

                writePair(
                        ctx,
                        "Assigned Driver",
                        getString(ad, "partyIdentifier")
                );
            }

            List<Map<String, Object>> covs =
                    getList(io, "coverages");

            for (Map<String, Object> c : covs) {

                writePair(
                        ctx,
                        "Coverage",
                        getString(c, "coverageName")
                );
            }

            ctx.y -= 8;
        }
    }

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
                "Cargo"
        );

        renderDynamicFields(
                ctx,
                cargo
        );
    }

    private void renderUnderwriting(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

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

            renderDynamicFields(
                    ctx,
                    q
            );
        }
    }

    private void renderPricing(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

        Map<String, Object> pricing =
                getMap(root, "pricing");

        if (pricing == null) {
            return;
        }

        renderSection(
                ctx,
                "Pricing"
        );

        List<Map<String, Object>> amounts =
                getList(pricing, "amounts");

        for (Map<String, Object> a : amounts) {

            writePair(
                    ctx,
                    getString(a, "amountTypeCode"),
                    getString(a, "amountValue")
                            + " "
                            + getString(a, "currencyCode")
            );
        }
    }

    private void renderTransaction(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

        renderSection(
                ctx,
                "Transaction"
        );

        writePair(
                ctx,
                "Transaction Identifier",
                getString(root, "transactionIdentifier")
        );

        writePair(
                ctx,
                "Transaction Type",
                getString(root, "transactionTypeCode")
        );

        writePair(
                ctx,
                "Transaction Status",
                getString(root, "transactionStatusCode")
        );

        writePair(
                ctx,
                "Policy Accept Date",
                getString(root, "policyAcceptDate")
        );
    }

    private void renderDynamicFields(
            Ctx ctx,
            Map<String, Object> map
    ) throws IOException {

        if (map == null) {
            return;
        }

        for (Map.Entry<String, Object> entry :
                map.entrySet()) {

            try {

                String key =
                        entry.getKey();

                Object value =
                        entry.getValue();

                if (value == null) {
                    continue;
                }

                if (value instanceof Map) {
                    continue;
                }

                if (value instanceof List) {

                    List<?> list =
                            (List<?>) value;

                    StringBuilder sb =
                            new StringBuilder();

                    for (Object o : list) {

                        sb.append(String.valueOf(o))
                                .append(", ");
                    }

                    writePair(
                            ctx,
                            camelToLabel(key),
                            sb.toString()
                    );

                    continue;
                }

                writePair(
                        ctx,
                        camelToLabel(key),
                        safe(String.valueOf(value))
                );

            } catch (Exception e) {

                log.error(
                        "FAILED FIELD : "
                                + entry.getKey(),
                        e
                );
            }
        }
    }

    private void drawFooters(
            Ctx ctx,
            Map<String, Object> root
    ) throws IOException {

        int total =
                ctx.doc.getNumberOfPages();

        for (int i = 0; i < total; i++) {

            PDPage page =
                    ctx.doc.getPage(i);

            PDPageContentStream footer =
                    new PDPageContentStream(
                            ctx.doc,
                            page,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true
                    );

            footer.beginText();

            footer.setFont(
                    PDType1Font.HELVETICA,
                    7
            );

            footer.setNonStrokingColor(MUTED);

            footer.newLineAtOffset(
                    PAGE_MARGIN,
                    20
            );

            footer.showText(
                    "Page "
                            + (i + 1)
                            + " of "
                            + total
            );

            footer.endText();

            footer.close();
        }
    }

    private void writeSubHeading(
            Ctx ctx,
            String text
    ) throws IOException {

        ensureSpace(ctx, LINE_HEIGHT);

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
                truncate(
                        safe(text),
                        90
                )
        );

        ctx.cs.endText();

        ctx.y -= LINE_HEIGHT;
    }

    private void writePair(
            Ctx ctx,
            String label,
            String value
    ) throws IOException {

        try {

            if (value == null || value.isBlank()) {
                return;
            }

            value = safe(value);

            ensureSpace(ctx, LINE_HEIGHT);

            log.info(
                    "WRITING FIELD : "
                            + label
                            + " = "
                            + value
            );

            System.out.println(
                    "WRITING FIELD : "
                            + label
                            + " = "
                            + value
            );

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
                    truncate(
                            safe(label + ":"),
                            40
                    )
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
                    truncate(value, 80)
            );

            ctx.cs.endText();

            ctx.y -= LINE_HEIGHT;

        } catch (Exception e) {

            log.error(
                    "FAILED TO WRITE FIELD : "
                            + label
                            + " = "
                            + value,
                    e
            );

            e.printStackTrace();
        }
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
                truncate(
                        safe(k + ":"),
                        28
                )
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

    private String getString(
            Map<String, Object> map,
            String key
    ) {

        Object value =
                map != null
                        ? map.get(key)
                        : null;

        return value != null
                ? String.valueOf(value)
                : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(
            Map<String, Object> map,
            String key
    ) {

        Object value =
                map != null
                        ? map.get(key)
                        : null;

        return value instanceof Map
                ? (Map<String, Object>) value
                : null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(
            Map<String, Object> map,
            String key
    ) {

        Object value =
                map != null
                        ? map.get(key)
                        : null;

        if (!(value instanceof List)) {

            return Collections.emptyList();
        }

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (Object item : (List<?>) value) {

            if (item instanceof Map) {

                result.add(
                        (Map<String, Object>) item
                );
            }
        }

        return result;
    }

    private String truncate(
            String value,
            int max
    ) {

        if (value == null) {
            return "";
        }

        return value.length() <= max
                ? value
                : value.substring(0, max - 1) + "...";
    }

    private String safe(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replaceAll("[^\\x20-\\x7E]", " ")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .replace("\f", " ")
                .trim();
    }

    private String camelToLabel(
            String camel
    ) {

        if (camel == null || camel.isBlank()) {
            return "";
        }

        String spaced =
                camel.replaceAll(
                        "([A-Z])",
                        " $1"
                ).trim();

        return Character.toUpperCase(
                spaced.charAt(0)
        ) + spaced.substring(1);
    }

    private String formatProductCode(
            String code
    ) {

        if (code == null || code.isBlank()) {
            return "";
        }

        String[] parts =
                code.split("_");

        StringJoiner joiner =
                new StringJoiner(" ");

        for (String part : parts) {

            if (part.isBlank()) {
                continue;
            }

            joiner.add(
                    Character.toUpperCase(
                            part.charAt(0)
                    ) + part.substring(1).toLowerCase()
            );
        }

        return joiner.toString();
    }

    // ========================= ADD CONSTANTS =========================

        private static final Set<String> REINST_SKIP =
                Collections.unmodifiableSet(
                        new HashSet<>(
                                Arrays.asList(
                                        "policyEvent",
                                        "pricing",
                                        "reinstateTermStartDate",
                                        "reinstateTermEndDate"
                                )
                        )
                );

        private static final Set<String> RENEWAL_SKIP =
                Collections.unmodifiableSet(
                        new HashSet<>(
                                Arrays.asList(
                                        "pricing",
                                        "termStartDate",
                                        "termEndDate",
                                        "insurableObjects"
                                )
                        )
                );

        private static final Set<String> ENDORSE_SKIP =
                Collections.unmodifiableSet(
                        new HashSet<>(
                                Arrays.asList(
                                        "_id",
                                        "policyEvent",
                                        "policyVersion",
                                        "changes",
                                        "pricing"
                                )
                        )
                );


        // ========================= REPLACE renderDynamicFields =========================

       private void renderDynamicFieldsWithSkip(
                        Ctx ctx,
                        Map<String, Object> map,
                        Set<String> skipKeys
                ) throws IOException {

                if (map == null) {
                        return;
                }

                for (Map.Entry<String, Object> entry :
                        map.entrySet()) {

                        try {

                        String key = entry.getKey();

                        Object value = entry.getValue();

                        if (skipKeys.contains(key)) {
                                continue;
                        }

                        if (value == null) {
                                continue;
                        }

                        if (value instanceof Map) {
                                continue;
                        }

                        if (value instanceof List) {
                                continue;
                        }

                        writePair(
                                ctx,
                                camelToLabel(key),
                                safe(String.valueOf(value))
                        );

                        } catch (Exception e) {

                        log.error(
                                "FAILED FIELD : "
                                        + entry.getKey(),
                                e
                        );
                        }
                }
        }


        // ========================= ADD POLICY LIFECYCLE =========================

        @SuppressWarnings("unchecked")
        private void addPolicyLifecycle(
                Ctx ctx,
                Map<String, Object> root
        ) throws IOException {

        // ================= CANCELLED =================

        if ("CANCELLED".equalsIgnoreCase(
                getString(root, "policyStatusCode")
        )) {

                renderSection(
                        ctx,
                        "Policy Cancellation"
                );

                writePair(
                        ctx,
                        "Cancelled Date",
                        getString(root, "policyCancelledDate")
                );

                writePair(
                        ctx,
                        "Cancel Type",
                        getString(root, "policyCancellationType")
                );

                writePair(
                        ctx,
                        "Comments",
                        getString(root, "policyCancellationComments")
                );

                ctx.y -= 6;
        }

        // ================= REINSTATEMENTS =================

        List<Map<String, Object>> reinsts =
                getList(root, "reinstatements");

        for (Map<String, Object> r : reinsts) {

                renderSection(
                        ctx,
                        "Policy Reinstatement"
                );

                writePair(
                        ctx,
                        "Reinstated Term",
                        getString(r, "reinstateTermStartDate")
                                + " to "
                                + getString(r, "reinstateTermEndDate")
                );

                Object eventObj =
                        r.get("policyEvent");

                if (eventObj instanceof Map) {

                writeSubHeading(
                        ctx,
                        "Policy Event:"
                );

                renderDynamicFields(
                        ctx,
                        (Map<String, Object>) eventObj
                );
                }

                Map<String, Object> pricing =
                        getMap(r, "pricing");

                if (pricing != null) {

                writeSubHeading(
                        ctx,
                        "Pricing"
                );

                renderAmountsTable(
                        ctx,
                        pricing
                );
                }

                renderDynamicFieldsWithSkip(
                        ctx,
                        r,
                        REINST_SKIP
                );

                ctx.y -= 6;
        }

        // ================= RENEWALS =================

        List<Map<String, Object>> renewals =
                getList(root, "renewals");

        for (Map<String, Object> r : renewals) {

                renderSection(
                        ctx,
                        "Policy Renewal"
                );

                writePair(
                        ctx,
                        "Renewal Term",
                        getString(r, "termStartDate")
                                + " -> "
                                + getString(r, "termEndDate")
                );

                renderDynamicFieldsWithSkip(
                        ctx,
                        r,
                        RENEWAL_SKIP
                );

                Map<String, Object> pricing =
                        getMap(r, "pricing");

                if (pricing != null) {

                writeSubHeading(
                        ctx,
                        "Pricing"
                );

                renderAmountsTable(
                        ctx,
                        pricing
                );
                }

                List<Map<String, Object>> objs =
                        getList(r, "insurableObjects");

                for (Map<String, Object> io : objs) {

                writeSubHeading(
                        ctx,
                        getString(io, "insurableObjectIdentifier")
                );

                renderDeepMap(
                        ctx,
                        io,
                        "  "
                );
                }

                ctx.y -= 6;
        }

        // ================= ENDORSEMENTS =================

        Object endorsementObj =
                root.get("endorsements");

        List<Map<String, Object>> endorsements =
                new ArrayList<>();

        if (endorsementObj instanceof List) {

                for (Object o :
                        (List<?>) endorsementObj) {

                if (o instanceof Map) {

                        endorsements.add(
                                (Map<String, Object>) o
                        );
                }
                }

        } else if (endorsementObj instanceof Map) {

                endorsements.add(
                        (Map<String, Object>) endorsementObj
                );
        }

        for (Map<String, Object> e : endorsements) {

                renderSection(
                        ctx,
                        "Policy Endorsement"
                );

                renderDynamicFieldsWithSkip(
                        ctx,
                        e,
                        ENDORSE_SKIP
                );

                Map<String, Object> pricing =
                        getMap(e, "pricing");

                if (pricing != null) {

                writeSubHeading(
                        ctx,
                        "Pricing"
                );

                renderAmountsTable(
                        ctx,
                        pricing
                );
                }

                Map<String, Object> policyEvent =
                        getMap(e, "policyEvent");

                if (policyEvent != null) {

                writeSubHeading(
                        ctx,
                        "Policy Event:"
                );

                renderDynamicFields(
                        ctx,
                        policyEvent
                );
                }

                Map<String, Object> version =
                        getMap(e, "policyVersion");

                if (version != null) {

                writeSubHeading(
                        ctx,
                        "Policy Version:"
                );

                renderDynamicFields(
                        ctx,
                        version
                );
                }

                Map<String, Object> changes =
                        getMap(e, "changes");

                if (changes != null) {

                renderSection(
                        ctx,
                        "Endorsement Changes"
                );

                for (Map.Entry<String, Object> entry :
                        changes.entrySet()) {

                        if (entry.getValue() instanceof Map) {

                        renderChangeGroup(
                                ctx,
                                camelToLabel(entry.getKey()),
                                (Map<String, Object>) entry.getValue()
                        );
                        }
                }
                }

                ctx.y -= 6;
        }
        }


        // ========================= LABEL ONLY =========================

        private void writeLabelOnly(
                Ctx ctx,
                String text
        ) throws IOException {

        ensureSpace(ctx, LINE_HEIGHT);

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

        ctx.y -= LINE_HEIGHT;
        }


        // ========================= AMOUNTS TABLE =========================

       private void renderAmountsTable(
        Ctx ctx,
        Map<String, Object> pricing
        ) throws IOException {

                List<Map<String, Object>> amounts =
                        getList(pricing, "amounts");

                if (amounts.isEmpty()) {
                        return;
                }

                for (Map<String, Object> a : amounts) {

                        String amountType =
                                getString(a, "amountTypeCode");

                        writeSubHeading(
                                ctx,
                                amountType
                        );

                        writePair(
                                ctx,
                                "Amount",
                                getString(a, "amountValue")
                        );

                        writePair(
                                ctx,
                                "Currency",
                                getString(a, "currencyCode")
                        );

                        ctx.y -= 4;
                }
        }


        // ========================= CHANGE GROUP =========================

        @SuppressWarnings("unchecked")
        private void renderChangeGroup(
                Ctx ctx,
                String title,
                Map<String, Object> block
        ) throws IOException {

        if (block == null) {
                return;
        }

        writeSubHeading(
                ctx,
                title
        );

        for (String bucket :
                Arrays.asList(
                        "added",
                        "updated",
                        "removed"
                )) {

                Object listObj =
                        block.get(bucket);

                if (!(listObj instanceof List)) {
                continue;
                }

                List<?> items =
                        (List<?>) listObj;

                if (items.isEmpty()) {
                continue;
                }

                writeSubHeading(
                        ctx,
                        bucket.toUpperCase()
                                + " ("
                                + items.size()
                                + ")"
                );

                int idx = 1;

                for (Object item : items) {

                if (item instanceof Map) {

                        writeSubHeading(
                                ctx,
                                "Record " + idx++
                        );

                        renderDeepMap(
                                ctx,
                                (Map<String, Object>) item,
                                "  "
                        );
                }
                }
        }
        }


        // ========================= DEEP MAP =========================

        @SuppressWarnings("unchecked")
        private void renderDeepMap(
                Ctx ctx,
                Map<String, Object> map,
                String indent
        ) throws IOException {

        if (map == null) {
                return;
        }

        for (Map.Entry<String, Object> entry :
                map.entrySet()) {

                String key =
                        entry.getKey();

                Object value =
                        entry.getValue();

                String label =
                        indent + camelToLabel(key);

                if (value instanceof Map) {

                writeLabelOnly(
                        ctx,
                        label + ":"
                );

                renderDeepMap(
                        ctx,
                        (Map<String, Object>) value,
                        indent + "  "
                );

                } else if (value instanceof List) {

                List<?> list =
                        (List<?>) value;

                int i = 1;

                for (Object o : list) {

                        if (o instanceof Map) {

                        writeSubHeading(
                                ctx,
                                "Item " + i++
                        );

                        renderDeepMap(
                                ctx,
                                (Map<String, Object>) o,
                                indent + "  "
                        );

                        } else {

                        writePair(
                                ctx,
                                label,
                                String.valueOf(o)
                        );
                        }
                }

                } else {

                writePair(
                        ctx,
                        label,
                        String.valueOf(value)
                );
                }
        }
        }
}
