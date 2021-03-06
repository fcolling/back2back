package org.ogerardin.b2b.files.md5;

import com.twmacinta.util.MD5;
import org.springframework.stereotype.Component;

/**
 * MD5 hash calculator using http://www.twmacinta.com/myjava/fast_md5.php via https://github.com/joyent/java-fast-md5
 */
@Component
public class FastMD5Calculator implements MD5Calculator {

    @Override
    public byte[] md5Hash(byte[] bytes) {
        MD5 md5 = new MD5();
        md5.Update(bytes);
        return md5.Final();
    }
}
