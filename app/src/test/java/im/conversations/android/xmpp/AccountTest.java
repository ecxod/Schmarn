package im.conversations.android.xmpp;

import im.conversations.android.IDs;
import im.conversations.android.database.model.Account;
import org.junit.Assert;
import org.junit.Test;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

public class AccountTest {

    @Test
    public void testEquals() throws XmppStringprepException {
        final var seed = IDs.seed();
        final var accountOne = new Account(1L, JidCreate.bareFrom("test@example.com"), seed);
        final var seedCopy = new byte[seed.length];
        System.arraycopy(seed, 0, seedCopy, 0, seedCopy.length);
        final var accountTwo = new Account(1L, JidCreate.bareFrom("test@example.com"), seedCopy);
        Assert.assertEquals(accountOne, accountTwo);
        Assert.assertEquals(accountOne.hashCode(), accountTwo.hashCode());
    }
}
