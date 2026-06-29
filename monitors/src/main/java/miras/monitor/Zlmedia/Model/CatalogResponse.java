package miras.monitor.Zlmedia.Model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

@JacksonXmlRootElement(localName = "Response")
public record CatalogResponse(
    @JacksonXmlProperty(localName = "CmdType") String cmdType,
    @JacksonXmlProperty(localName = "SN") String sn,
    @JacksonXmlProperty(localName = "DeviceID") String deviceId,
    @JacksonXmlProperty(localName = "SumNum") Integer sumNum
) {}
