/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2013 Adobe
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package com.adobe.acs.commons.images.impl;

import com.adobe.acs.commons.images.ImageTransformer;
import com.adobe.acs.commons.images.NamedImageTransformer;
import com.adobe.acs.commons.util.OsgiPropertyUtil;
import com.adobe.acs.commons.util.TypeUtil;
import com.adobe.acs.commons.wcm.ComponentHelper;
import com.day.image.Layer;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Component(
        label = "ACS AEM Commons - Named Image Transformer Factory",
        description = "",
        configurationFactory = true,
        metatype = true
)
@Reference(
        name = "imageTransformers",
        referenceInterface = ImageTransformer.class,
        policy = ReferencePolicy.DYNAMIC,
        cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE
)
@Service
public class NamedImageTransformerImpl implements NamedImageTransformer {
    private final Logger log = LoggerFactory.getLogger(NamedImageTransformerImpl.class);

    private Map<String, ImageTransformer> imageTransformers = new HashMap<String, ImageTransformer>();

    @Reference
    private ComponentHelper componentHelper;

    /* Transformer Configuration Name */
    private static final String DEFAULT_TRANSFORM_NAME = "";

    @Property(label = "Transform Name",
            description = "Name of Transform.",
            value = "")
    private static final String PROP_NAME = "name";

    private String transformName = DEFAULT_TRANSFORM_NAME;

    /* Image Transform Configurations */
    @Property(label = "Image Transformers",
            description = "Transform in the format [ image-transfomer-type:key1=val1&key2=val2 ]"
                    + " Order of transform rules dictates order of application.",
            cardinality = Integer.MAX_VALUE,
            value = { })
    private static final String PROP_TRANSFORMS = "transforms";

    private LinkedHashMap<String, ValueMap> transforms = new LinkedHashMap<String, ValueMap>();


    /**
     * @param layer
     * @return
     * @throws IOException
     * @throws RepositoryException
     */
    public Layer transform(Layer layer) throws IOException, RepositoryException {

        for (final String type : this.transforms.keySet()) {
            final ImageTransformer imageTransformer = this.imageTransformers.get(type);
            if (imageTransformer == null) {
                log.warn("Skipping transform. Missing ImageTransformer for type: {}");
                continue;
            }

            final ValueMap transformParams = this.transforms.get(type);
            layer = imageTransformer.transform(layer, transformParams);
        }

        return layer;
    }

    @Activate
    protected final void activate(final Map<String, String> properties) throws Exception {
        this.transformName = PropertiesUtil.toString(properties.get(PROP_NAME), DEFAULT_TRANSFORM_NAME);

        log.info("Registering Named Image Transfomer: {}", this.transformName);

        final Map<String, String> map = OsgiPropertyUtil.toMap(PropertiesUtil.toStringArray(
                properties.get(PROP_TRANSFORMS), new String[]{ }), ":", true, null);


        for (final Map.Entry<String, String> entry : map.entrySet()) {
            final String[] params = StringUtils.split(entry.getValue(), "&");
            final Map<String, String> values = OsgiPropertyUtil.toMap(params, "=", true, null);

            log.debug("ImageTransform params for [ {} ] ~> {}", entry.getKey(), values);

            // Order matters so use a LinkedHashMap
            this.transforms.put(entry.getKey(), TypeUtil.toValueMap(values));
        }

        log.info("Named Images Transforms: {}", this.transforms.size());
        for (final Map.Entry<String, ValueMap> entry : this.transforms.entrySet()) {
            log.info("{} ~> {}", entry.getKey(), entry.getValue());
        }
    }

    // Bind
    protected void bindImageTransformers(final ImageTransformer service, final Map<Object, Object> props) {
        final String type = PropertiesUtil.toString(props.get(ImageTransformer.PROP_TYPE), null);
        if (type != null) {
            imageTransformers.put(type, service);
        }
    }

    // Unbind
    protected void unbindImageTransformers(final ImageTransformer service, final Map<Object, Object> props) {
        final String type = PropertiesUtil.toString(props.get(ImageTransformer.PROP_TYPE), null);
        if (type != null) {
            imageTransformers.remove(type);
        }
    }
}