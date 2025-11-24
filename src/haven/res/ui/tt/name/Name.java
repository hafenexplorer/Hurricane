/* Preprocessed source code */
package haven.res.ui.tt.name;
import haven.*;
import java.awt.image.BufferedImage;

/* >tt: Name */
@haven.FromResource(name = "ui/tt/name", version = 5)
public class Name extends ItemInfo.Tip {
    public final Text nm;

    public Name(Owner owner, Text nm) {
	super(owner);
	this.nm = nm;
    }

    public BufferedImage tipimg() {
	return(nm.img);
    }

    public int order() {return(5);}

    public Tip shortvar() {return(this);}

    public static Name mkinfo(Owner owner, Object... args) {
	return(new Name(owner, Text.render((String)args[1])));
    }
}
