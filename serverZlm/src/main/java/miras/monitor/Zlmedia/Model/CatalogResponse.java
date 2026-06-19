package miras.monitor.Zlmedia.Model;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

@Data
@JacksonXmlRootElement(localName = "Response")
public class CatalogResponse {

    @JacksonXmlProperty(localName = "CmdType")
    private String cmdType;

    @JacksonXmlProperty(localName = "SN")
    private String sn;

    @JacksonXmlProperty(localName = "DeviceID")
    private String deviceId;

    @JacksonXmlProperty(localName = "SumNum")
    private Integer sumNum;
}
