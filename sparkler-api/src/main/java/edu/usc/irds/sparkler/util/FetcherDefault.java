package edu.usc.irds.sparkler.util;

import edu.usc.irds.sparkler.AbstractExtensionPoint;
import edu.usc.irds.sparkler.Fetcher;
import edu.usc.irds.sparkler.model.FetchedData;
import edu.usc.irds.sparkler.model.Resource;
import edu.usc.irds.sparkler.model.ResourceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.function.Function;

/**
 * This class is a default implementation of {@link Fetcher} contract.
 * This fetcher doesn't depends on external libraries,
 * instead it uses URLConnection provided by JDK to fetch the resources.
 *
 */
public class FetcherDefault extends AbstractExtensionPoint
        implements Fetcher, Function<Resource, FetchedData> {

    public static final Logger LOG = LoggerFactory.getLogger(FetcherDefault.class);
    public static final Integer CONNECT_TIMEOUT = 5000; // Milliseconds. FIXME: Get from Configuration
    public static final Integer READ_TIMEOUT = 10000; // Milliseconds. FIXME: Get from Configuration
    public static final Long CONTENT_LIMIT = 8388608L; // Bytes. FIXME: Get from Configuration
    public static final Integer DEFAULT_ERROR_CODE = 400;

    @Override
    public Iterator<FetchedData> fetch(Iterator<Resource> resources) throws Exception {
        return new StreamTransformer<>(resources, this);
    }

    public FetchedData fetch(Resource resource) throws Exception {
        LOG.info("DEFAULT FETCHER {}", resource.getUrl());
        URLConnection urlConn = new URL(resource.getUrl()).openConnection();
        urlConn.setConnectTimeout(CONNECT_TIMEOUT);
        urlConn.setReadTimeout(READ_TIMEOUT);
        int responseCode = ((HttpURLConnection)urlConn).getResponseCode();
        LOG.debug("STATUS CODE : " + responseCode + " " + resource.getUrl());
        try (InputStream inStream = urlConn.getInputStream()) {
            ByteArrayOutputStream bufferOutStream = new ByteArrayOutputStream();
            // 4kb buffer
            byte[] buffer = new byte[4096];
            int read = 0;
            while((read = inStream.read(buffer, 0, buffer.length)) != -1) {
                bufferOutStream.write(buffer, 0, read);
                if (bufferOutStream.size() >= CONTENT_LIMIT) {
                    LOG.info("Size is greater than the allowed limit. Truncating the data.");
                    break;
                }
            }
            bufferOutStream.flush();
            byte[] rawData = bufferOutStream.toByteArray();
            FetchedData fetchedData = new FetchedData(rawData, urlConn.getContentType(), responseCode);
            resource.setStatus(ResourceStatus.FETCHED.toString());
            fetchedData.setResource(resource);
            return fetchedData;
        }
    }

    @Override
    public FetchedData apply(Resource resource) {
        try {
            return this.fetch(resource);
        } catch (Exception e) {
            int statusCode =  DEFAULT_ERROR_CODE;
            if (e instanceof FileNotFoundException){
                statusCode = 404;
            }
            LOG.warn("FETCH-ERROR {}", resource.getUrl());
            LOG.debug(e.getMessage(), e);
            FetchedData fetchedData = new FetchedData(new byte[0], "", statusCode);
            resource.setStatus(ResourceStatus.ERROR.toString());
            fetchedData.setResource(resource);
            return fetchedData;
        }
    }

}
