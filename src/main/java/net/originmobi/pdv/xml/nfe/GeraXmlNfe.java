package net.originmobi.pdv.xml.nfe;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import net.originmobi.pdv.model.NotaFiscal;
import java.util.HashMap;
import java.util.Map;

public class GeraXmlNfe {

    public Map<String, String> gerarXML(NotaFiscal notaFiscal) {
        XStream xstream = new XStream(new DomDriver());
        ConversorXmlNfe conversor = new ConversorXmlNfe();
        AssinaXML assina = new AssinaXML();

        xstream.registerConverter(conversor);
        xstream.alias("enviNFe", NotaFiscal.class);

        String xmlNaoAssinado = xstream.toXML(notaFiscal);
        String xmlAssinado = assina.assinaXML(xmlNaoAssinado);

        // Pega a chave da NFe que foi gerada durante a conversão
        String chaveNfe = conversor.retornaChaveNfe();

        Map<String, String> resultado = new HashMap<>();
        resultado.put("chave", chaveNfe);
        resultado.put("xml", xmlAssinado);

        return resultado;
    }
}