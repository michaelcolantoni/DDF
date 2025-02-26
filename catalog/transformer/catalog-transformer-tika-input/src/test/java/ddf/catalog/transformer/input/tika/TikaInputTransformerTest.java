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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static junit.framework.Assert.assertNotNull;

import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Hashtable;
import java.util.TimeZone;

import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

import ddf.catalog.content.operation.ContentMetadataExtractor;
import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.MetacardType;
import ddf.catalog.data.impl.AttributeDescriptorImpl;
import ddf.catalog.data.impl.BasicTypes;
import ddf.catalog.data.impl.MetacardTypeImpl;
import ddf.catalog.data.impl.types.AssociationsAttributes;
import ddf.catalog.data.impl.types.ContactAttributes;
import ddf.catalog.data.impl.types.LocationAttributes;
import ddf.catalog.data.impl.types.MediaAttributes;
import ddf.catalog.data.impl.types.ValidationAttributes;
import ddf.catalog.data.types.Core;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.InputTransformer;

public class TikaInputTransformerTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TikaInputTransformerTest.class);

    private static final String COMMON_METACARDTYPE = "common";

    private static final String SOUND = "Sound";

    private static final String IMAGE = "Image";

    private static final String TEXT = "Text";

    private static final String DOCUMENT = "Document";

    private static final String DATASET = "Dataset";

    private static final String COLLECTION = "Collection";

    @Test
    public void testRegisterService() {
        BundleContext mockBundleContext = mock(BundleContext.class);
        TikaInputTransformer tikaInputTransformer = new TikaInputTransformer(mockBundleContext,
                getCommonMetacardType());
        verify(mockBundleContext).registerService(eq(InputTransformer.class), eq(
                tikaInputTransformer), any(Hashtable.class));
    }

    @Test
    public void testTransformWithContentExtractor() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testPDF.pdf");

        Bundle bundleMock = mock(Bundle.class);
        BundleContext bundleCtx = mock(BundleContext.class);
        ContentMetadataExtractor cme = mock(ContentMetadataExtractor.class);
        ServiceReference serviceRef = mock(ServiceReference.class);

        when(bundleMock.getBundleContext()).thenReturn(bundleCtx);
        when(bundleCtx.getService(any())).thenReturn(cme);
        ImmutableSet<AttributeDescriptor> attributeDescriptors =
                ImmutableSet.of(new AttributeDescriptorImpl("attr1",
                        false,
                        false,
                        false,
                        false,
                        BasicTypes.OBJECT_TYPE), new AttributeDescriptorImpl("attr2",
                        false,
                        false,
                        false,
                        false,
                        BasicTypes.OBJECT_TYPE));
        when(cme.getMetacardAttributes()).thenReturn(attributeDescriptors);

        TikaInputTransformer tikaInputTransformer = new TikaInputTransformer(bundleCtx,
                getCommonMetacardType()) {
            @Override
            Bundle getBundle() {
                return bundleMock;
            }
        };
        tikaInputTransformer.addContentMetadataExtractors(serviceRef);
        Metacard metacard = tikaInputTransformer.transform(stream);

        assertThat(metacard.getMetacardType()
                .getName(), is(COMMON_METACARDTYPE));
        int matchedAttrs = (int) metacard.getMetacardType()
                .getAttributeDescriptors()
                .stream()
                .filter(attributeDescriptors::contains)
                .count();

        assertThat(matchedAttrs, is(attributeDescriptors.size()));
    }

    @Test
    public void testContentExtractorRemoval() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testPDF.pdf");

        Bundle bundleMock = mock(Bundle.class);
        BundleContext bundleCtx = mock(BundleContext.class);
        ContentMetadataExtractor cme = mock(ContentMetadataExtractor.class);
        ServiceReference serviceRef = mock(ServiceReference.class);

        when(bundleMock.getBundleContext()).thenReturn(bundleCtx);
        when(bundleCtx.getService(any())).thenReturn(cme);
        ImmutableSet<AttributeDescriptor> attributeDescriptors =
                ImmutableSet.of(new AttributeDescriptorImpl("attr1",
                        false,
                        false,
                        false,
                        false,
                        BasicTypes.OBJECT_TYPE), new AttributeDescriptorImpl("attr2",
                        false,
                        false,
                        false,
                        false,
                        BasicTypes.OBJECT_TYPE));
        when(cme.getMetacardAttributes()).thenReturn(attributeDescriptors);

        TikaInputTransformer tikaInputTransformer = new TikaInputTransformer(bundleCtx,
                getCommonMetacardType()) {
            @Override
            Bundle getBundle() {
                return bundleMock;
            }
        };
        tikaInputTransformer.addContentMetadataExtractors(serviceRef);
        tikaInputTransformer.removeContentMetadataExtractor(serviceRef);
        Metacard metacard = tikaInputTransformer.transform(stream);

        assertThat(metacard.getMetacardType()
                .getName(), is(COMMON_METACARDTYPE));
        int matchedAttrs = (int) metacard.getMetacardType()
                .getAttributeDescriptors()
                .stream()
                .filter(attributeDescriptors::contains)
                .count();

        assertThat(matchedAttrs, is(0));
    }

    @Test(expected = CatalogTransformerException.class)
    public void testNullInputStream() throws Exception {
        transform(null);
    }

    @Test
    public void testJavaClass() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("CatalogFrameworkImpl.class");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("CatalogFrameworkImpl"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString("DEFAULT_RESOURCE_NOT_FOUND_MESSAGE"));
        assertThat(metacard.getContentTypeName(), is("application/java-vm"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(DOCUMENT));
    }

    @Test
    public void testAudioWav() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testWAV.wav");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString("16Int"));
        assertThat(metacard.getContentTypeName(), is("audio/x-wav"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(SOUND));
    }

    @Test
    public void testAudioAiff() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testAIFF.aif");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString("PCM_SIGNED"));
        assertThat(metacard.getContentTypeName(), is("audio/x-aiff"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(SOUND));
    }

    @Test
    public void testAudioAu() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testAU.au");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString("PCM_SIGNED"));
        assertThat(metacard.getContentTypeName(), is("audio/basic"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(SOUND));
    }

    @Test
    public void testAudioMidi() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testMID.mid");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString("PPQ"));
        assertThat(metacard.getContentTypeName(), is("audio/midi"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(SOUND));
    }

    @Test
    public void testJavaSource() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testpackage/testJAVA.java");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString("HelloWorld"));
        assertThat(metacard.getContentTypeName(), containsString("text/plain"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(TEXT));
    }

    @Test
    public void testCppSource() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testCPP.cpp");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString("Hello world example"));
        assertThat(metacard.getContentTypeName(), containsString("text/plain"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(TEXT));
    }

    @Test
    public void testGroovySource() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testGROOVY.groovy");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString("this is a comment"));
        assertThat(metacard.getContentTypeName(), containsString("text/plain"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(TEXT));
    }

    @Test
    public void testTiff() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testTIFF.tif");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<meta name=\"tiff:BitsPerSample\" content=\"8\"/>"));
        assertThat(metacard.getContentTypeName(), is("image/tiff"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(IMAGE));
    }

    @Test
    public void testBmp() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testBMP.bmp");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<meta name=\"Compression CompressionTypeName\" content=\"BI_RGB\"/>"));
        assertThat(metacard.getContentTypeName(), is("image/x-ms-bmp"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(IMAGE));
    }

    @Test
    public void testGif() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testGIF.gif");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<meta name=\"Compression CompressionTypeName\" content=\"lzw\"/>"));
        assertThat(metacard.getContentTypeName(), is("image/gif"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(IMAGE));
    }

    @Test
    public void testGeoTaggedJpeg() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testJPEG_GEO.jpg");

        /*
         * The dates in testJPED_GEO.jpg do not contain timezones. If no timezone is specified,
         * the Tika input transformer assumes the local time zone.  Set the system timezone to UTC
         * so we can do assertions.
         */
        TimeZone defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<meta name=\"Model\" content=\"Canon EOS 40D\"/>"));
        assertThat(metacard.getContentTypeName(), is("image/jpeg"));
        assertThat(convertDate(metacard.getCreatedDate()), is("2009-08-11 09:09:45 UTC"));
        assertThat(convertDate(metacard.getModifiedDate()), is("2009-10-02 23:02:49 UTC"));
        assertThat(metacard.getAttribute(Metacard.GEOGRAPHY)
                .getValue(), is("POINT(-54.1234 12.54321)"));

        // Reset timezone back to local time zone.
        TimeZone.setDefault(defaultTimeZone);

        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(IMAGE));
        assertThat(metacard.getContentTypeName(), is("image/jpeg"));
    }

    @Test
    public void testCommentedJpeg() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testJPEG_commented.jpg");

        /*
         * The dates in testJPEG_commented.jpg do not contain timezones. If no timezone is specified,
         * the Tika input transformer assumes the local time zone.  Set the system timezone to UTC
         * so we can do assertions.
         */
        TimeZone defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Tosteberga \u00C4ngar"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<meta name=\"Keywords\" content=\"bird watching\"/>"));
        assertThat(metacard.getContentTypeName(), is("image/jpeg"));
        assertThat(convertDate(metacard.getCreatedDate()), is("2010-07-28 11:02:00 UTC"));

        // Reset timezone back to local time zone.
        TimeZone.setDefault(defaultTimeZone);
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(IMAGE));
        assertThat(metacard.getContentTypeName(), is("image/jpeg"));
    }

    @Test
    public void testPng() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testPNG.png");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<meta name=\"Compression Lossless\" content=\"true\"/>"));
        assertThat(metacard.getContentTypeName(), is("image/png"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(IMAGE));
    }

    @Test
    public void testMp3() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testMP3id3v1_v2.mp3");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Test Title"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<meta name=\"xmpDM:artist\" content=\"Test Artist\"/>"));
        assertThat(metacard.getContentTypeName(), is("audio/mpeg"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(SOUND));
    }

    @Test
    public void testMp4() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testMP4.m4a");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Test Title"));
        assertThat(convertDate(metacard.getCreatedDate()), is("2012-01-28 18:39:18 UTC"));
        assertThat(convertDate(metacard.getModifiedDate()), is("2012-01-28 18:40:25 UTC"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<meta name=\"xmpDM:artist\" content=\"Test Artist\"/>"));
        assertThat(metacard.getContentTypeName(), is("audio/mp4"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(SOUND));
    }

    @Test
    public void testPDF() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testPDF.pdf");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Apache Tika - Apache Tika"));
        assertThat(convertDate(metacard.getCreatedDate()), is("2007-09-15 09:02:31 UTC"));
        assertThat(convertDate(metacard.getModifiedDate()), is("2007-09-15 09:02:31 UTC"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<meta name=\"xmpTPg:NPages\" content=\"1\"/>"));
        assertThat(metacard.getContentTypeName(), is("application/pdf"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(DOCUMENT));
    }

    @Test
    public void testXml() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testXML.xml");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Test Document"));
        assertThat(convertDate(metacard.getCreatedDate()), is("2000-12-01 00:00:00 UTC"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString("John Smith"));
        assertThat(metacard.getContentTypeName(), is("application/xml"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(DOCUMENT));
    }

    @Test
    public void testWordDoc() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testWORD.docx");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Sample Word Document"));
        assertThat(convertDate(metacard.getCreatedDate()), is("2008-12-11 16:04:00 UTC"));
        assertThat(convertDate(metacard.getModifiedDate()), is("2010-11-12 16:21:00 UTC"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<p>This is a sample Microsoft Word Document.</p>"));
        assertThat(metacard.getContentTypeName(), is(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(DOCUMENT));
    }

    @Test
    public void testPpt() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testPPT.ppt");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Sample Powerpoint Slide"));
        assertThat(convertDate(metacard.getCreatedDate()), is("2007-09-14 17:33:12 UTC"));
        assertThat(convertDate(metacard.getModifiedDate()), is("2007-09-14 19:16:39 UTC"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString("Created with Microsoft"));
        assertThat(metacard.getContentTypeName(), is("application/vnd.ms-powerpoint"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(DOCUMENT));
    }

    @Test
    public void testPptx() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testPPT.pptx");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Attachment Test"));
        assertThat(convertDate(metacard.getCreatedDate()), is("2010-05-04 06:43:54 UTC"));
        assertThat(convertDate(metacard.getModifiedDate()), is("2010-06-29 06:34:35 UTC"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "content as every other file being tested for tika content parsing"));
        assertThat(metacard.getContentTypeName(), is(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(DOCUMENT));
    }

    @Test
    public void testXls() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testEXCEL.xls");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Simple Excel document"));
        assertThat(convertDate(metacard.getCreatedDate()), is("2007-10-01 16:13:56 UTC"));
        assertThat(convertDate(metacard.getModifiedDate()), is("2007-10-01 16:31:43 UTC"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "Written and saved in Microsoft Excel X for Mac Service Release 1."));
        assertThat(metacard.getContentTypeName(), is("application/vnd.ms-excel"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(DOCUMENT));
    }

    @Test
    public void testXlsx() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testEXCEL.xlsx");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Simple Excel document"));
        assertThat(convertDate(metacard.getCreatedDate()), is("2007-10-01 16:13:56 UTC"));
        assertThat(convertDate(metacard.getModifiedDate()), is("2008-12-11 16:02:17 UTC"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "Sample Excel Worksheet - Numbers and their Squares"));
        assertThat(metacard.getContentTypeName(), is(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(DOCUMENT));
    }

    @Test
    public void testZip() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testZIP.zip");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), nullValue());
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<meta name=\"Content-Type\" content=\"application/zip\"/>"));
        assertThat(metacard.getContentTypeName(), is(
                "application/zip"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(COLLECTION));
    }

    @Test
    public void testEmail() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testEmail.eml");
        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Welcome"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "<meta name=\"Content-Type\" content=\"message/rfc822\"/>"));
        assertThat(metacard.getContentTypeName(), is(
                "message/rfc822"));
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(DATASET));
    }

    @Test
    public void testOpenOffice() throws Exception {
        InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("testOpenOffice2.odt");

        /*
         * The dates in testOpenOffice2.odt do not contain timezones. If no timezone is specified,
         * the Tika input transformer assumes the local time zone.  Set the system timezone to UTC
         * so we can do assertions.
         */
        TimeZone defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Metacard metacard = transform(stream);
        assertNotNull(metacard);
        assertThat(metacard.getTitle(), is("Test OpenOffice2 Document"));
        assertThat(convertDate(metacard.getCreatedDate()), is("2007-09-14 11:06:08 UTC"));
        assertThat(convertDate(metacard.getModifiedDate()), is("2013-02-13 06:52:10 UTC"));
        assertNotNull(metacard.getMetadata());
        assertThat(metacard.getMetadata(), containsString(
                "This is a sample Open Office document, written in NeoOffice 2.2.1"));
        assertThat(metacard.getContentTypeName(), is("application/vnd.oasis.opendocument.text"));

        // Reset timezone back to local time zone.
        TimeZone.setDefault(defaultTimeZone);
        assertThat(metacard.getAttribute(Core.DATATYPE)
                .getValue(), is(DOCUMENT));
    }

    private String convertDate(Date date) {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        LOGGER.debug(df.format(date));
        return df.format(date);
    }

    private Metacard transform(InputStream stream) throws Exception {
        TikaInputTransformer tikaInputTransformer = new TikaInputTransformer(null,
                getCommonMetacardType());
        Metacard metacard = tikaInputTransformer.transform(stream);
        return metacard;
    }

    private static MetacardType getCommonMetacardType() {
        return new MetacardTypeImpl(COMMON_METACARDTYPE, Arrays.asList(new ValidationAttributes(),
                new ContactAttributes(),
                new LocationAttributes(),
                new MediaAttributes(),
                new AssociationsAttributes()));
    }

}
