/*
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2017 Rebasing.xyz ReBot
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package xyz.rebasing.rebot.plugin.welcome.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import xyz.rebasing.rebot.plugin.welcome.kogito.WelcomeChallenge;

public class WelcomeChallengeTest {

    @Test
    public void testWelcomeChallenge() {
        WelcomeChallenge challenge = new WelcomeChallenge("admin");
        Assertions.assertTrue(challenge.getNumber1() >= 0 && challenge.getNumber1() <= 10);
        Assertions.assertTrue(challenge.getNumber2() >= 0 && challenge.getNumber2() <= 10);
        Assertions.assertEquals("admin", challenge.getUser());
        Assertions.assertEquals(challenge.result(), Common.challengeResult(challenge));
        challenge.setAnswer(Common.challengeResult(challenge));
        Assertions.assertEquals(false, challenge.isKickUser());
    }
}
