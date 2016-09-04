package pro.jness.pdf.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.opensagres.xdocreport.converter.ConverterTypeTo;
import fr.opensagres.xdocreport.converter.ConverterTypeVia;
import fr.opensagres.xdocreport.converter.Options;
import fr.opensagres.xdocreport.core.XDocReportException;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.TemplateEngineKind;
import fr.opensagres.xdocreport.template.formatter.FieldsMetadata;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.jness.pdf.dto.SourcesData;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * @author Aleksandr Streltsov (jness.pro@gmail.com)
 *         on 26/08/16
 */
public class PdfGenerationTask implements Callable<PdfGenerationResult> {

    private static final Logger logger = LoggerFactory.getLogger(ClassNameUtil.getCurrentClassName());

    private SourcesData sourcesData;

    private JsonElement data;
    private IXDocReport ixDocReport;
    private IContext iContext;

    public PdfGenerationTask(SourcesData sourcesData) {
        this.sourcesData = sourcesData;
    }

    @Override
    public PdfGenerationResult call() throws Exception {
        try (BufferedReader streamReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(sourcesData.getData()), "UTF-8"))) {
            StringBuilder responseStrBuilder = new StringBuilder();

            String inputStr;
            while ((inputStr = streamReader.readLine()) != null)
                responseStrBuilder.append(inputStr);
            JsonParser parser = new JsonParser();
            data = parser.parse(responseStrBuilder.toString());
            if (!data.isJsonObject()) {
                throw new IllegalStateException("Data is not a valid json");
            }

            File result = new File(sourcesData.getData().getParent(), sourcesData.getTaskId() + ".pdf");
            try (FileOutputStream out = new FileOutputStream(result)) {
                initReportAndContext();
                Options options = Options.getTo(ConverterTypeTo.PDF).via(ConverterTypeVia.ODFDOM);
                fillContext();
                ixDocReport.convert(iContext, options, out);
            }
        } catch (freemarker.core.ParseException e) {
            logger.error(e.getMessage(), e);
            return new PdfGenerationResult(PdfCreationStatus.FAILED, e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return new PdfGenerationResult(PdfCreationStatus.FAILED, e.getMessage());
        }
        return new PdfGenerationResult(PdfCreationStatus.DONE);
    }

    private void initReportAndContext() throws XDocReportException, IOException {
        ixDocReport = XDocReportRegistry.getRegistry().loadReport(
                new FileInputStream(sourcesData.getTemplate()), TemplateEngineKind.Freemarker);
        iContext = ixDocReport.createContext();
    }

    private void fillContext() throws NotFoundException, CannotCompileException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException, XDocReportException {
        for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonObject()) {
                addTableRows(entry.getValue());
            } else {
                logger.debug("key: {}, value: {}", entry.getKey(), entry.getValue());
                iContext.put(entry.getKey(), entry.getValue().isJsonNull() ? "" : entry.getValue().getAsString());
            }
        }
    }

    private void addTableRows(JsonElement rowsElement) throws XDocReportException, NotFoundException, CannotCompileException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        JsonObject jo = rowsElement.getAsJsonObject();
        if (jo.get("rows").isJsonArray()) {
            String rowKey = jo.get("key").getAsString();
            JsonArray rows = jo.get("rows").getAsJsonArray();

            if (rows.size() > 0) {
                final Map<String, Class<?>> properties = new HashMap<>();
                for (Map.Entry<String, JsonElement> rowData : rows.get(0).getAsJsonObject().entrySet()) {
                    logger.debug("{}: {}", rowData.getKey(), rowData.getValue());
                    properties.put(rowData.getKey(), String.class);
                }
                String className = UUID.randomUUID().toString().replaceAll("-", "");
                Class<?> clazz = PojoGenerator.generate(className, properties);
                FieldsMetadata fieldsMetadata = ixDocReport.createFieldsMetadata();
                fieldsMetadata.load(rowKey, clazz, true);
                ArrayList rowList = new ArrayList();

                for (JsonElement row : rows) {
                    Object obj = clazz.newInstance();
                    for (Map.Entry<String, JsonElement> rowData : row.getAsJsonObject().entrySet()) {
                        clazz.getMethod("set" + Character.toUpperCase(rowData.getKey().charAt(0)) + rowData.getKey().substring(1), String.class)
                                .invoke(obj, rowData.getValue().isJsonNull() ? "" : rowData.getValue().getAsString());

                        logger.debug("{}: {}", rowData.getKey(), rowData.getValue().isJsonNull() ? null : rowData.getValue().getAsString());
                    }
                    rowList.add(obj);
                }
                iContext.put(rowKey, rowList);
            } else {
                FieldsMetadata fieldsMetadata = ixDocReport.createFieldsMetadata();
                fieldsMetadata.load(rowKey, String.class, true);
                iContext.put(rowKey, new ArrayList());
            }
        } else {
            throw new IllegalStateException("object: \n" + jo.toString() + "\n must have an array named 'rows'");
        }
    }

    public SourcesData getSourcesData() {
        return sourcesData;
    }
}