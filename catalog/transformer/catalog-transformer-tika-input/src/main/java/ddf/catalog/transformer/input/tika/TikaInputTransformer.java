/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.catalog.transformer.input.tika;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.TeeContentHandler;
import org.apache.tika.sax.ToTextContentHandler;
import org.apache.tika.sax.ToXMLContentHandler;
import org.codice.ddf.platform.util.TemporaryFileBackedOutputStream;
import org.imgscalr.Scalr;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.XMLReaderFactory;

import com.github.jaiimageio.impl.plugins.tiff.TIFFImageReaderSpi;
import com.github.jaiimageio.jpeg2000.impl.J2KImageReaderSpi;

import ddf.catalog.content.operation.ContentMetadataExtractor;
import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.MetacardType;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.data.impl.MetacardTypeImpl;
import ddf.catalog.data.types.Core;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.InputTransformer;
import ddf.catalog.transformer.common.tika.MetacardCreator;
import ddf.catalog.transformer.common.tika.TikaMetadataExtractor;
import ddf.catalog.util.impl.ServiceComparator;

public class TikaInputTransformer implements InputTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TikaInputTransformer.class);

    private Templates templates = null;

    private Map<ServiceReference, ContentMetadataExtractor> contentMetadataExtractors =
            Collections.synchronizedMap(new TreeMap<>(new ServiceComparator()));

    private MetacardType metacardType = null;

    private static final Map<com.google.common.net.MediaType, String>
            SPECIFIC_MIME_TYPE_DATA_TYPE_MAP;

    private static final Map<com.google.common.net.MediaType, String>
            FALLBACK_MIME_TYPE_DATA_TYPE_MAP;

    private static final String OVERALL_FALLBACK_DATA_TYPE = "Dataset";

    static {
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP = new HashMap<>();
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.MICROSOFT_EXCEL,
                "Document");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.MICROSOFT_POWERPOINT,
                "Document");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.MICROSOFT_WORD,
                "Document");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.OPENDOCUMENT_GRAPHICS,
                "Document");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.OPENDOCUMENT_PRESENTATION,
                "Document");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.OPENDOCUMENT_SPREADSHEET,
                "Document");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.OPENDOCUMENT_TEXT,
                "Document");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.APPLICATION_BINARY,
                "Document");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.PDF, "Document");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.APPLICATION_XML_UTF_8,
                "Text");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.JSON_UTF_8, "Text");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.KML, "Text");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.ZIP, "Collection");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.TAR, "Collection");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.GZIP, "Collection");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.BZIP2, "Collection");
        SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.OCTET_STREAM,
                OVERALL_FALLBACK_DATA_TYPE);

        FALLBACK_MIME_TYPE_DATA_TYPE_MAP = new HashMap<>();
        FALLBACK_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.ANY_APPLICATION_TYPE,
                "Document");
        FALLBACK_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.ANY_IMAGE_TYPE,
                "Image");
        FALLBACK_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.ANY_TEXT_TYPE, "Text");
        FALLBACK_MIME_TYPE_DATA_TYPE_MAP.put(com.google.common.net.MediaType.ANY_AUDIO_TYPE,
                "Sound");
    }

    public void addContentMetadataExtractors(
            ServiceReference<ContentMetadataExtractor> contentMetadataExtractorRef) {
        Bundle bundle = getBundle();
        if (bundle != null) {
            ContentMetadataExtractor cme = bundle.getBundleContext()
                    .getService(contentMetadataExtractorRef);
            contentMetadataExtractors.put(contentMetadataExtractorRef, cme);
        }
    }

    Bundle getBundle() {
        return FrameworkUtil.getBundle(TikaInputTransformer.class);
    }

    public void removeContentMetadataExtractor(
            ServiceReference<ContentMetadataExtractor> contentMetadataExtractorRef) {
        contentMetadataExtractors.remove(contentMetadataExtractorRef);
    }

    public TikaInputTransformer(BundleContext bundleContext, MetacardType metacardType) {

        this.metacardType = metacardType;

        ClassLoader tccl = Thread.currentThread()
                .getContextClassLoader();
        try {
            Thread.currentThread()
                    .setContextClassLoader(getClass().getClassLoader());
            templates =
                    TransformerFactory.newInstance(net.sf.saxon.TransformerFactoryImpl.class.getName(),
                            net.sf.saxon.TransformerFactoryImpl.class.getClassLoader())
                            .newTemplates(new StreamSource(TikaMetadataExtractor.class.getResourceAsStream(
                                    "/metadata.xslt")));
        } catch (TransformerConfigurationException e) {
            LOGGER.debug("Couldn't create XML transformer", e);
        } finally {
            Thread.currentThread()
                    .setContextClassLoader(tccl);
        }

        if (bundleContext == null) {
            LOGGER.info("Bundle context is null. Unable to register {} as an osgi service.",
                    TikaInputTransformer.class.getSimpleName());
            return;
        }

        registerService(bundleContext);
        IIORegistry.getDefaultInstance()
                .registerServiceProvider(new J2KImageReaderSpi());
        IIORegistry.getDefaultInstance()
                .registerServiceProvider(new TIFFImageReaderSpi());
    }

    @Override
    public Metacard transform(InputStream input) throws IOException, CatalogTransformerException {
        return transform(input, null);
    }

    @Override
    public Metacard transform(InputStream input, String id)
            throws IOException, CatalogTransformerException {
        LOGGER.debug("Transforming input stream using Tika.");

        if (input == null) {
            throw new CatalogTransformerException("Cannot transform null input.");
        }

        try (TemporaryFileBackedOutputStream fileBackedOutputStream = new TemporaryFileBackedOutputStream()) {
            try {
                IOUtils.copy(input, fileBackedOutputStream);
            } catch (IOException e) {
                throw new CatalogTransformerException("Could not copy bytes of content message.",
                        e);
            }

            Parser parser = new AutoDetectParser();
            ToXMLContentHandler xmlContentHandler = new ToXMLContentHandler();
            ToTextContentHandler textContentHandler = null;
            ContentHandler contentHandler;
            if (!contentMetadataExtractors.isEmpty()) {
                textContentHandler = new ToTextContentHandler();
                contentHandler = new TeeContentHandler(xmlContentHandler, textContentHandler);
            } else {
                contentHandler = xmlContentHandler;
            }

            TikaMetadataExtractor tikaMetadataExtractor = new TikaMetadataExtractor(parser,
                    contentHandler);

            Metadata metadata;
            try (InputStream inputStreamCopy = fileBackedOutputStream.asByteSource()
                    .openStream()) {
                metadata = tikaMetadataExtractor.parseMetadata(inputStreamCopy, new ParseContext());
            }

            String metadataText = xmlContentHandler.toString();
            if (templates != null) {
                metadataText = transformToXml(metadataText);
            }

            Metacard metacard;
            if (textContentHandler != null) {
                String plainText = textContentHandler.toString();

                Set<AttributeDescriptor> attributes = contentMetadataExtractors.values()
                        .stream()
                        .map(ContentMetadataExtractor::getMetacardAttributes)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());
                MetacardTypeImpl extendedMetacardType = new MetacardTypeImpl(metacardType.getName(),
                        metacardType,
                        attributes);

                metacard = MetacardCreator.createMetacard(metadata,
                        id,
                        metadataText,
                        extendedMetacardType);

                for (ContentMetadataExtractor contentMetadataExtractor : contentMetadataExtractors.values()) {
                    contentMetadataExtractor.process(plainText, metacard);
                }
            } else {
                metacard = MetacardCreator.createMetacard(metadata, id, metadataText, metacardType);
            }

            String metacardContentType = metacard.getContentTypeName();
            if (StringUtils.isNotBlank(metacardContentType)) {
                metacard.setAttribute(new AttributeImpl(Core.DATATYPE, getDatatype(
                        metacardContentType)));
            }

            if (StringUtils.startsWith(metacardContentType, "image")) {
                try (InputStream inputStreamCopy = fileBackedOutputStream.asByteSource()
                        .openStream()) {
                    createThumbnail(inputStreamCopy, metacard);
                }
            }

            LOGGER.debug("Finished transforming input stream using Tika.");
            return metacard;
        }
    }

    @Nullable
    private String getDatatype(String mimeType) {
        if (mimeType == null) {
            return null;
        }

        com.google.common.net.MediaType mediaType = com.google.common.net.MediaType.parse(mimeType);

        LOGGER.debug("Attempting to map {}", mimeType);
        Optional<Map.Entry<com.google.common.net.MediaType, String>> returnType =
                SPECIFIC_MIME_TYPE_DATA_TYPE_MAP.entrySet()
                        .stream()
                        .filter(mediaTypeStringEntry -> mediaType.is(mediaTypeStringEntry.getKey()))
                        .findFirst();

        if (!returnType.isPresent()) {
            Optional<Map.Entry<com.google.common.net.MediaType, String>> fallback =
                    FALLBACK_MIME_TYPE_DATA_TYPE_MAP.entrySet()
                            .stream()
                            .filter(mediaTypeStringEntry -> mediaType.is(mediaTypeStringEntry.getKey()))
                            .findFirst();

            if (!fallback.isPresent()) {
                return OVERALL_FALLBACK_DATA_TYPE;
            }

            return fallback.get()
                    .getValue();
        }

        return returnType.get()
                .getValue();
    }

    /**
     * We programmatically register the Tika Input Transformer so we can programmatically build the
     * list of supported mime types.
     */
    private void registerService(BundleContext bundleContext) {
        LOGGER.debug("Registering {} as an osgi service.",
                TikaInputTransformer.class.getSimpleName());
        bundleContext.registerService(ddf.catalog.transform.InputTransformer.class,
                this,
                getServiceProperties());
    }

    private Hashtable<String, Object> getServiceProperties() {
        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put(ddf.catalog.Constants.SERVICE_ID, "tika");
        properties.put(ddf.catalog.Constants.SERVICE_TITLE, "Tika Input Transformer");
        properties.put(ddf.catalog.Constants.SERVICE_DESCRIPTION,
                "The Tika Input Transformer detects and extracts metadata and text content from various documents.");
        properties.put("mime-type", getSupportedMimeTypes());
        // The Tika Input Transformer should be tried last, so we set the service ranking to -1
        properties.put(Constants.SERVICE_RANKING, -1);

        return properties;
    }

    private List<String> getSupportedMimeTypes() {
        MediaTypeRegistry mediaTypeRegistry = MediaTypeRegistry.getDefaultRegistry();

        Set<MediaType> mediaTypes = mediaTypeRegistry.getTypes();
        Set<MediaType> mediaTypeAliases = new HashSet<>();
        List<String> mimeTypes = new ArrayList<>(mediaTypes.size());

        for (MediaType mediaType : mediaTypes) {
            addMediaTypetoMimeTypes(mediaType, mimeTypes);
            mediaTypeAliases.addAll(mediaTypeRegistry.getAliases(mediaType));
        }

        for (MediaType mediaType : mediaTypeAliases) {
            addMediaTypetoMimeTypes(mediaType, mimeTypes);
        }

        mimeTypes.add("image/jp2");

        LOGGER.debug("supported mime types: {}", mimeTypes);
        return mimeTypes;
    }

    private void addMediaTypetoMimeTypes(MediaType mediaType, List<String> mimeTypes) {
        String mimeType = mediaType.getType() + "/" + mediaType.getSubtype();
        mimeTypes.add(mimeType);
    }

    private void createThumbnail(InputStream input, Metacard metacard) {
        try {
            Image image = ImageIO.read(new CloseShieldInputStream(input));

            if (null != image) {
                BufferedImage bufferedImage = new BufferedImage(image.getWidth(null),
                        image.getHeight(null),
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = bufferedImage.createGraphics();
                graphics.drawImage(image, null, null);
                graphics.dispose();

                BufferedImage thumb = Scalr.resize(bufferedImage, 200);

                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    ImageIO.write(thumb, "jpeg", out);

                    byte[] thumbBytes = out.toByteArray();
                    metacard.setAttribute(new AttributeImpl(Metacard.THUMBNAIL, thumbBytes));
                }
            } else {
                LOGGER.debug("Unable to read image from input stream to create thumbnail.");
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to read image from input stream to create thumbnail.", e);
        }
    }

    private String transformToXml(String xhtml) {
        LOGGER.debug("Transforming xhtml to xml.");

        XMLReader xmlReader = null;
        try {
            XMLReader xmlParser = XMLReaderFactory.createXMLReader();
            xmlParser.setFeature("http://xml.org/sax/features/external-general-entities", false);
            xmlParser.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            xmlParser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            xmlReader = new XMLFilterImpl(xmlParser);
        } catch (SAXException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        if (xmlReader != null) {
            try {
                Writer xml = new StringWriter();
                Transformer transformer = templates.newTransformer();
                transformer.transform(new SAXSource(xmlReader, new InputSource(new StringReader(
                        xhtml))), new StreamResult(xml));
                return xml.toString();
            } catch (TransformerException e) {
                LOGGER.debug("Unable to transform metadata from XHTML to XML.", e);
            }
        }
        return xhtml;
    }
}
