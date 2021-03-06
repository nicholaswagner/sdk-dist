/*
 * Copyright (c) 2002-2008 LWJGL Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'LWJGL' nor the names of
 *   its contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * 3-D gear wheels. Originally by Brian Paul
 */
package tv.twitch.sample;

import java.nio.FloatBuffer;

import org.lwjgl.input.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.LWJGLUtil;
import org.lwjgl.Sys;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.lwjgl.opengl.GLContext;

import tv.twitch.AuthToken;
import tv.twitch.ErrorCode;
import tv.twitch.broadcast.*;
import tv.twitch.chat.*;
import tv.twitch.chat.ChatController.ChatState;
import tv.twitch.broadcast.BroadcastController.BroadcastState;

import static org.lwjgl.opengl.ARBTransposeMatrix.*;
import static org.lwjgl.opengl.GL11.*;


/**
 * <p>
 * This is the OpenGL "standard" Gears demo, originally by Brian Paul
 * </p>
 * @author Brian Matzon <brian@matzon.dk>
 * @version $Revision$
 * $Id$
 */
public class Gears
{
    private float	view_rotx	= 20.0f;
    private float	view_roty	= 30.0f;
    private float	view_rotz;
    private int		gear1;
    private int		gear2;
    private int		gear3;
    private float	angle;

    private int broadcastFramesPerSecond = 30;
    private String[] chatChannels = new String[]{ "<channel>" };
    private String username = "";
    private String password = "";
    private String clientId = "";
    private String clientSecret = "";

    private BroadcastController broadcastController = null;
    private long lastCaptureTime = 0;						//!< The timestamp of the last frame capture.
    private long metaDataSpanSequenceId = -1; 
    private IngestTester ingestTester = null;

    private ChatController chatController = null;
    private ChatController.EmoticonMode emoticonParsingMode = ChatController.EmoticonMode.Url;
    private boolean enableChat = true; 

    private BroadcastController.Listener broadcastListener = new BroadcastController.Listener()
    {
        @Override
        public void onAuthTokenRequestComplete(ErrorCode result, AuthToken authToken)
        {
        }

        @Override
        public void onLoginAttemptComplete(ErrorCode result)
        {
            if (ErrorCode.succeeded(result))
            {
                if (enableChat)
                {
                    initChat();
                }
            }
        }

        @Override
        public void onGameNameListReceived(ErrorCode result, GameInfo[] list)
        {
            if (ErrorCode.succeeded(result))
            {
                System.out.println("Game names:");
                for (int i=0; i<list.length; ++i)
                {
                    System.out.println("    " + list[i].name);
                }
            }
        }

        @Override
        public void onBroadcastStateChanged(BroadcastState state)
        {
            System.out.println("BroadcastState: " + state.toString());
        }

        @Override
        public void onLoggedOut()
        {
        }

        @Override
        public void onStreamInfoUpdated(StreamInfo info)
        {
            System.out.println("Num viewers: " + info.viewers);
        }

        @Override
        public void onIngestListReceived(IngestList list)
        {
        }

        @Override
        public void onFrameSubmissionIssue(ErrorCode result)
        {
            System.out.println("FrameSubmissionIssue: " + result.toString());
        }

        @Override
        public void onBroadcastStarted()
        {
            System.out.println("BroadcastStarted");
        }

        @Override
        public void onBroadcastStopped()
        {
            System.out.println("BroadcastStopped");
        }

        @Override
        public void onStartFailure(ErrorCode result) 
        {
            System.out.println("onStartFailure: " + result.toString());
        }
    };

    private ChatController.Listener chatListener = new ChatController.Listener()
    {
        @Override
        public void onInitializationComplete(ErrorCode result) 
        {
            if (ErrorCode.failed(result))
            {
                chatController = null;
            }
        }

        @Override
        public void onShutdownComplete(ErrorCode result) 
        {
            if (ErrorCode.succeeded(result))
            {
                chatController = null;
            }
        }

        @Override
        public void onChatStateChanged(ChatState state) 
        {
            System.out.println("ChatState: " + state.toString());
        }

        @Override
        public void onEmoticonDataAvailable()
        {
            System.out.println("onEmoticonDataAvailable");
        }

        @Override
        public void onEmoticonDataExpired()
        {
            System.out.println("onEmoticonDataExpired");
        }

        @Override
        public void onTokenizedMessagesReceived(String channelName, ChatTokenizedMessage[] messages)
        {
            System.out.println("Received tokenized chat messages:");

            StringBuffer sb = new StringBuffer();

            for (int i = 0; i < messages.length; ++i)
            {
                ChatTokenizedMessage msg = messages[i];
                sb.append("    <").append(channelName).append("> ").append(msg.displayName).append(": ");

                for (int t = 0; t < msg.tokenList.length; ++t)
                {
                    ChatMessageToken token = msg.tokenList[t];
                    switch (token.type)
                    {
                    case TTV_CHAT_MSGTOKEN_TEXT:
                    {
                        ChatTextMessageToken mt = (ChatTextMessageToken)token;
                        sb.append(mt.text);
                        break;
                    }
                    case TTV_CHAT_MSGTOKEN_TEXTURE_IMAGE:
                    {
                        ChatTextureImageMessageToken mt = (ChatTextureImageMessageToken)token;
                        sb.append( String.format("[%d,%d,%d,%d,%d]", mt.sheetIndex, mt.x1, mt.y1, mt.x2, mt.y2) );
                        break;
                    }
                    case TTV_CHAT_MSGTOKEN_URL_IMAGE:
                    {
                        ChatUrlImageMessageToken mt = (ChatUrlImageMessageToken)token;
                        sb.append("[").append(mt.url).append("]");
                        break;
                    }
                    }
                }
                sb.append("\n");
            }

            System.out.println(sb.toString());
        }

        @Override
        public void onRawMessagesReceived(String channelName, ChatRawMessage[] messages)
        {
            System.out.println("Received raw chat messages:");

            StringBuffer sb = new StringBuffer();

            for (int i = 0; i < messages.length; ++i)
            {
                ChatRawMessage msg = messages[i];
                sb.append("    <").append(channelName).append("> ").append(msg.userName).append(": ").append(messages[i].message).append("\n");
            }

            System.out.println(sb.toString());
        }

        @Override
        public void onUsersChanged(String channelName, ChatUserInfo[] joinList, ChatUserInfo[] leaveList, ChatUserInfo[] userInfoList)
        {
            for (int i = 0; i < leaveList.length; ++i)
            {
                System.out.println("<" + channelName + "> User left: " + leaveList[i].displayName);
            }

            for (int i = 0; i < userInfoList.length; ++i)
            {
                System.out.println("<" + channelName + "> User changed: " + userInfoList[i].displayName);
            }

            for (int i = 0; i < joinList.length; ++i)
            {
                System.out.println("<" + channelName + "> User joined: " + joinList[i].displayName);
            }
        }

        @Override
        public void onConnected(String channelName)
        {
            System.out.println("Connected to channel: " + channelName);
        }

        @Override
        public void onDisconnected(String channelName)
        {
            System.out.println("Disconnected from channel: " + channelName);
        }

        @Override
        public void onMessagesCleared(String channelName, String username)
        {
            if (username == null || username.equals(""))
            {
                System.out.println("Messages cleared: " + channelName);
            }
            else
            {
                System.out.println("Messages cleared for channel and user: " + channelName + " " + username);
            }
        }

        @Override
        public void onBadgeDataAvailable(String channelName) 
        {
            System.out.println("onBadgeDataAvailable: " + channelName);
        }

        @Override
        public void onBadgeDataExpired(String channelName)
        {
            System.out.println("onBadgeDataExpired: " + channelName);
        }
    };

    private IngestTester.Listener ingestTesterListener = new IngestTester.Listener()
    {
        @Override
        public void onIngestTestStateChanged(IngestTester source, IngestTester.TestState state)
        {
            String str = "[" + (int)(source.getTotalProgress() * 100) + "%] " + state.toString();

            switch (state)
            {
            case ConnectingToServer:
            {
                str += ": " + source.getCurrentServer().serverName + "...";
                break;
            }
            case DoneTestingServer:
            {
                str += ": " + source.getCurrentServer().serverName + "... " + source.getCurrentServer().bitrateKbps + " kbps";
                break;
            }
            case Finished:
            case Cancelled:
            {
                source.setListener(null);
                ingestTester = null;
                break;
            }
            default:
            {
                break;
            }
            }

            if (state == IngestTester.TestState.Finished)
            {
                if (source.getIngestList().getBestServer() != null)
                {
                    str += ": Selecting best server - " + source.getIngestList().getBestServer().serverName;
                    broadcastController.setIngestServer(source.getIngestList().getBestServer());
                }
            }

            System.out.println(str);
        }
    };


    public static void main(String[] args) {
        new Gears().execute();
        System.exit(0);
    }

    /**
     *
     */
    private void execute() 
    {
        try 
        {
            init();
            initBroadcasting();
        } 
        catch (LWJGLException le) 
        {
            le.printStackTrace();
            System.out.println("Failed to initialize Gears.");
            return;
        }

        loop();

        shutdownChat();
        shutdownBroadcasting();
        destroy();
    }

    /**
     *
     */
    private void destroy() {

        Display.destroy();
    }

    /**
     *
     */
    private void loop() 
    {
        long lastFrameTime = 0;

        while (!Display.isCloseRequested())
        {
            long now = System.nanoTime();
            long renderFps = 30;
            long nanoPerFrame = 1000000000 / renderFps;

            // update the animation
            if (now - lastFrameTime >= nanoPerFrame)
            {
                lastFrameTime = now;

                angle += 2.0f;

                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                glPushMatrix();
                glRotatef(view_rotx, 1.0f, 0.0f, 0.0f);
                glRotatef(view_roty, 0.0f, 1.0f, 0.0f);
                glRotatef(view_rotz, 0.0f, 0.0f, 1.0f);

                glPushMatrix();
                glTranslatef(-3.0f, -2.0f, 0.0f);
                glRotatef(angle, 0.0f, 0.0f, 1.0f);
                glCallList(gear1);
                glPopMatrix();

                glPushMatrix();
                glTranslatef(3.1f, -2.0f, 0.0f);
                glRotatef(-2.0f * angle - 9.0f, 0.0f, 0.0f, 1.0f);
                glCallList(gear2);
                glPopMatrix();

                glPushMatrix();
                glTranslatef(-3.1f, 4.2f, 0.0f);
                glRotatef(-2.0f * angle - 25.0f, 0.0f, 0.0f, 1.0f);
                glCallList(gear3);
                glPopMatrix();

                glPopMatrix();

                Display.update();
            }		

            handleInput();

            if (broadcastController != null)
            {
                submitFrame();
                broadcastController.update();
            }

            if (chatController != null)
            {
                chatController.update();
            }
        }
    }

    private String getActiveChatChannelName()
    {
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT))
        {
            return chatChannels[Math.min(1, chatChannels.length-1)];
        }
        else
        {
            return chatChannels[0];
        }
    }

    private void handleInput()
    {
        while (Keyboard.next())
        {
            if (Keyboard.isRepeatEvent())
            {
                continue;
            }

            if (!Keyboard.getEventKeyState())
            {
                continue;
            }

            if (Keyboard.getEventKey() == Keyboard.KEY_A) 
            {
                broadcastController.requestAuthToken(username, password);
            }
            else if (Keyboard.getEventKey() == Keyboard.KEY_S)
            {
                broadcastController.setStreamInfo(username, "Java Game", "Fun times");
            }
            else if (Keyboard.getEventKey() == Keyboard.KEY_P) 
            {
                if (broadcastController.getIsPaused())
                {
                    broadcastController.resumeBroadcasting();
                }
                else
                {
                    broadcastController.pauseBroadcasting();
                }
            }
            else if (Keyboard.getEventKey() == Keyboard.KEY_SPACE) 
            {
                if (broadcastController.getIsBroadcasting())
                {
                    broadcastController.stopBroadcasting();
                }
                else
                {
                    VideoParams videoParams = broadcastController.getRecommendedVideoParams(Display.getDisplayMode().getWidth(), Display.getDisplayMode().getHeight(), broadcastFramesPerSecond);
                    videoParams.verticalFlip = true;

                    broadcastController.startBroadcasting(videoParams);
                }
            }
            else if (Keyboard.getEventKey() == Keyboard.KEY_R) 
            {
                broadcastController.runCommercial();
            }
            else if (Keyboard.getEventKey() == Keyboard.KEY_I) 
            {
                if (ingestTester != null)
                {
                    broadcastController.cancelIngestTest();
                    ingestTester = null;
                }
                else
                {
                    ingestTester = broadcastController.startIngestTest();
                    if (ingestTester != null)
                    {
                        ingestTester.setListener(ingestTesterListener);
                    }
                }
            }
            else if (Keyboard.getEventKey() == Keyboard.KEY_G) 
            {
                broadcastController.requestGameNameList("final");
            }
            else if (Keyboard.getEventKey() == Keyboard.KEY_1) 
            {
                broadcastController.sendActionMetaData("TestAction", broadcastController.getCurrentBroadcastTime(), "Something cool happened", "{ \"MyValue\" : \"42\" }");
            }
            else if (Keyboard.getEventKey() == Keyboard.KEY_2) 
            {
                if (metaDataSpanSequenceId == -1)
                {
                    metaDataSpanSequenceId = broadcastController.startSpanMetaData("TestSpan", broadcastController.getCurrentBroadcastTime(), "Something cool just started happening", "{ \"MyValue\" : \"42\" }");
                }
                else
                {
                    broadcastController.endSpanMetaData("TestSpan", broadcastController.getCurrentBroadcastTime(), metaDataSpanSequenceId, "Something cool just stopped happening", "{ \"MyValue\" : \"42\" }");
                    metaDataSpanSequenceId = -1;
                }
            }
            else if (Keyboard.getEventKey() == Keyboard.KEY_C) 
            {
                String channelName = getActiveChatChannelName();

                if (chatController != null && 
                        chatController.getCurrentState() == ChatController.ChatState.Initialized)
                {
                    if (chatController.getIsConnected(channelName))
                    {
                        chatController.disconnect(channelName);
                    }
                    else
                    {
                        chatController.connect(channelName);
                    }
                }
            }
            else if (Keyboard.getEventKey() == Keyboard.KEY_V) 
            {
                String channelName = getActiveChatChannelName();

                if (chatController != null && 
                        chatController.getCurrentState() == ChatController.ChatState.Initialized)
                {
                    if (chatController.getIsConnected(channelName))
                    {
                        chatController.disconnect(channelName);
                    }
                    else
                    {
                        chatController.connectAnonymous(channelName);
                    }
                }
            }
            else if (Keyboard.getEventKey() == Keyboard.KEY_M) 
            {
                String channelName = getActiveChatChannelName();

                if (chatController != null && 
                        chatController.getCurrentState() == ChatController.ChatState.Initialized &&
                        chatController.getIsConnected(channelName))
                {
                    chatController.sendChatMessage(channelName, "Test chat message: " + System.currentTimeMillis());
                }
            }
        }
    }

    private void init() throws LWJGLException 
    {
        // create Window of size 800x600
        Display.setDisplayMode(new DisplayMode(1024, 768));
        Display.setLocation((Display.getDisplayMode().getWidth() - 300) / 2, (Display.getDisplayMode().getHeight() - 300) / 2);
        Display.setTitle("Gears");

        try {
            Display.create();
        } catch (LWJGLException e) {
            // This COULD be because of a bug! A delay followed by a new attempt is supposed to fix it.
            e.printStackTrace();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }

            Display.create();
        }

        // setup ogl
        FloatBuffer pos = BufferUtils.createFloatBuffer(4).put(new float[] { 5.0f, 5.0f, 10.0f, 0.0f});
        FloatBuffer red = BufferUtils.createFloatBuffer(4).put(new float[] { 0.8f, 0.1f, 0.0f, 1.0f});
        FloatBuffer green = BufferUtils.createFloatBuffer(4).put(new float[] { 0.0f, 0.8f, 0.2f, 1.0f});
        FloatBuffer blue = BufferUtils.createFloatBuffer(4).put(new float[] { 0.2f, 0.2f, 1.0f, 1.0f});

        pos.flip();
        red.flip();
        green.flip();
        blue.flip();

        glLight(GL_LIGHT0, GL_POSITION, pos);
        glEnable(GL_CULL_FACE);
        glEnable(GL_LIGHTING);
        glEnable(GL_LIGHT0);
        glEnable(GL_DEPTH_TEST);

        /* make the gears */
        gear1 = glGenLists(1);
        glNewList(gear1, GL_COMPILE);
        glMaterial(GL_FRONT, GL_AMBIENT_AND_DIFFUSE, red);
        gear(1.0f, 4.0f, 1.0f, 20, 0.7f);
        glEndList();

        gear2 = glGenLists(1);
        glNewList(gear2, GL_COMPILE);
        glMaterial(GL_FRONT, GL_AMBIENT_AND_DIFFUSE, green);
        gear(0.5f, 2.0f, 2.0f, 10, 0.7f);
        glEndList();

        gear3 = glGenLists(1);
        glNewList(gear3, GL_COMPILE);
        glMaterial(GL_FRONT, GL_AMBIENT_AND_DIFFUSE, blue);
        gear(1.3f, 2.0f, 0.5f, 10, 0.7f);
        glEndList();

        glEnable(GL_NORMALIZE);

        glMatrixMode(GL_PROJECTION);

        System.err.println("LWJGL: " + Sys.getVersion() + " / " + LWJGLUtil.getPlatformName());
        System.err.println("GL_VENDOR: " + glGetString(GL_VENDOR));
        System.err.println("GL_RENDERER: " + glGetString(GL_RENDERER));
        System.err.println("GL_VERSION: " + glGetString(GL_VERSION));
        System.err.println();
        System.err.println("glLoadTransposeMatrixfARB() supported: " + GLContext.getCapabilities().GL_ARB_transpose_matrix);
        if (!GLContext.getCapabilities().GL_ARB_transpose_matrix) {
            // --- not using extensions
            glLoadIdentity();
        } else {
            // --- using extensions
            final FloatBuffer identityTranspose = BufferUtils.createFloatBuffer(16).put(
                    new float[] { 1, 0, 0, 0, 0, 1, 0, 0,
                            0, 0, 1, 0, 0, 0, 0, 1});
            identityTranspose.flip();
            glLoadTransposeMatrixARB(identityTranspose);
        }

        float h = (float) 300 / (float) 300;
        glFrustum(-1.0f, 1.0f, -h, h, 5.0f, 60.0f);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glTranslatef(0.0f, 0.0f, -40.0f);
    }


    private void submitFrame()
    {
        if (!broadcastController.getIsBroadcasting() || broadcastController.getIsPaused())
        {
            return;
        }

        long curTime = System.nanoTime();
        long nanoPerFrame = 1000000000 / broadcastFramesPerSecond;

        // If you send frames too quickly to the SDK (based on the broadcast FPS you configured) it will not be able 
        // to make use of them all.  In that case, it will simply release buffers without using them which means the
        // game wasted time doing the capture.  To mitigate this, the app should pace the captures to the broadcast FPS.
        long captureDelta = curTime - lastCaptureTime;
        boolean isTimeForNextCapture = captureDelta >= nanoPerFrame;

        if (!isTimeForNextCapture)
        {
            return;
        }

        FrameBuffer buffer = broadcastController.getNextFreeBuffer();
        broadcastController.captureFrameBuffer_ReadPixels(buffer);
        broadcastController.submitFrame(buffer);

        lastCaptureTime = curTime;
    }

    private void initBroadcasting()
    {
        if (broadcastController != null)
        {
            return;
        }

        broadcastController = new BroadcastController();
        broadcastController.setListener(broadcastListener);

        broadcastController.setClientId(clientId);
        broadcastController.setClientSecret(clientSecret);

        if (!broadcastController.initialize())
        {
            broadcastController = null;
        }		
    }

    private void shutdownBroadcasting()
    {
        if (broadcastController == null)
        {
            return;
        }

        // it's best not to force a sync shutdown and wait for all async operations to finish but we're exiting the sample
        broadcastController.forceSyncShutdown();
        broadcastController.setListener(null);
    }

    private void initChat()
    {
        if (chatController != null)
        {
            return;
        }

        chatController = new ChatController();
        chatController.setClientId(clientId);
        chatController.setClientSecret(clientSecret);
        chatController.setListener(chatListener);
        chatController.setUserName(username);
        chatController.setAuthToken(broadcastController.getAuthToken());
        chatController.setEmoticonParsingModeMode(emoticonParsingMode);

        if (!chatController.initialize())
        {
            chatController = null;
        }
    }

    private void shutdownChat()
    {
        if (chatController == null)
        {
            return;
        }

        // it's best not to force a sync shutdown and wait for the shutdown callback but we're exiting the sample
        chatController.forceSyncShutdown();
    }

    /**
     * Draw a gear wheel.  You'll probably want to call this function when
     * building a display list since we do a lot of trig here.
     *
     * @param inner_radius radius of hole at center
     * @param outer_radius radius at center of teeth
     * @param width width of gear
     * @param teeth number of teeth
     * @param tooth_depth depth of tooth
     */
    private void gear(float inner_radius, float outer_radius, float width, int teeth, float tooth_depth) {
        int i;
        float r0, r1, r2;
        float angle, da;
        float u, v, len;

        r0 = inner_radius;
        r1 = outer_radius - tooth_depth / 2.0f;
        r2 = outer_radius + tooth_depth / 2.0f;

        da = 2.0f * (float) Math.PI / teeth / 4.0f;

        glShadeModel(GL_FLAT);

        glNormal3f(0.0f, 0.0f, 1.0f);

        /* draw front face */
        glBegin(GL_QUAD_STRIP);
        for (i = 0; i <= teeth; i++) {
            angle = i * 2.0f * (float) Math.PI / teeth;
            glVertex3f(r0 * (float) Math.cos(angle), r0 * (float) Math.sin(angle), width * 0.5f);
            glVertex3f(r1 * (float) Math.cos(angle), r1 * (float) Math.sin(angle), width * 0.5f);
            if (i < teeth) {
                glVertex3f(r0 * (float) Math.cos(angle), r0 * (float) Math.sin(angle), width * 0.5f);
                glVertex3f(r1 * (float) Math.cos(angle + 3.0f * da), r1 * (float) Math.sin(angle + 3.0f * da),
                        width * 0.5f);
            }
        }
        glEnd();

        /* draw front sides of teeth */
        glBegin(GL_QUADS);
        for (i = 0; i < teeth; i++) {
            angle = i * 2.0f * (float) Math.PI / teeth;
            glVertex3f(r1 * (float) Math.cos(angle), r1 * (float) Math.sin(angle), width * 0.5f);
            glVertex3f(r2 * (float) Math.cos(angle + da), r2 * (float) Math.sin(angle + da), width * 0.5f);
            glVertex3f(r2 * (float) Math.cos(angle + 2.0f * da), r2 * (float) Math.sin(angle + 2.0f * da), width * 0.5f);
            glVertex3f(r1 * (float) Math.cos(angle + 3.0f * da), r1 * (float) Math.sin(angle + 3.0f * da), width * 0.5f);
        }
        glEnd();

        /* draw back face */
        glBegin(GL_QUAD_STRIP);
        for (i = 0; i <= teeth; i++) {
            angle = i * 2.0f * (float) Math.PI / teeth;
            glVertex3f(r1 * (float) Math.cos(angle), r1 * (float) Math.sin(angle), -width * 0.5f);
            glVertex3f(r0 * (float) Math.cos(angle), r0 * (float) Math.sin(angle), -width * 0.5f);
            glVertex3f(r1 * (float) Math.cos(angle + 3 * da), r1 * (float) Math.sin(angle + 3 * da), -width * 0.5f);
            glVertex3f(r0 * (float) Math.cos(angle), r0 * (float) Math.sin(angle), -width * 0.5f);
        }
        glEnd();

        /* draw back sides of teeth */
        glBegin(GL_QUADS);
        for (i = 0; i < teeth; i++) {
            angle = i * 2.0f * (float) Math.PI / teeth;
            glVertex3f(r1 * (float) Math.cos(angle + 3 * da), r1 * (float) Math.sin(angle + 3 * da), -width * 0.5f);
            glVertex3f(r2 * (float) Math.cos(angle + 2 * da), r2 * (float) Math.sin(angle + 2 * da), -width * 0.5f);
            glVertex3f(r2 * (float) Math.cos(angle + da), r2 * (float) Math.sin(angle + da), -width * 0.5f);
            glVertex3f(r1 * (float) Math.cos(angle), r1 * (float) Math.sin(angle), -width * 0.5f);
        }
        glEnd();

        /* draw outward faces of teeth */
        glBegin(GL_QUAD_STRIP);
        for (i = 0; i < teeth; i++) {
            angle = i * 2.0f * (float) Math.PI / teeth;
            glVertex3f(r1 * (float) Math.cos(angle), r1 * (float) Math.sin(angle), width * 0.5f);
            glVertex3f(r1 * (float) Math.cos(angle), r1 * (float) Math.sin(angle), -width * 0.5f);
            u = r2 * (float) Math.cos(angle + da) - r1 * (float) Math.cos(angle);
            v = r2 * (float) Math.sin(angle + da) - r1 * (float) Math.sin(angle);
            len = (float) Math.sqrt(u * u + v * v);
            u /= len;
            v /= len;
            glNormal3f(v, -u, 0.0f);
            glVertex3f(r2 * (float) Math.cos(angle + da), r2 * (float) Math.sin(angle + da), width * 0.5f);
            glVertex3f(r2 * (float) Math.cos(angle + da), r2 * (float) Math.sin(angle + da), -width * 0.5f);
            glNormal3f((float) Math.cos(angle), (float) Math.sin(angle), 0.0f);
            glVertex3f(r2 * (float) Math.cos(angle + 2 * da), r2 * (float) Math.sin(angle + 2 * da), width * 0.5f);
            glVertex3f(r2 * (float) Math.cos(angle + 2 * da), r2 * (float) Math.sin(angle + 2 * da), -width * 0.5f);
            u = r1 * (float) Math.cos(angle + 3 * da) - r2 * (float) Math.cos(angle + 2 * da);
            v = r1 * (float) Math.sin(angle + 3 * da) - r2 * (float) Math.sin(angle + 2 * da);
            glNormal3f(v, -u, 0.0f);
            glVertex3f(r1 * (float) Math.cos(angle + 3 * da), r1 * (float) Math.sin(angle + 3 * da), width * 0.5f);
            glVertex3f(r1 * (float) Math.cos(angle + 3 * da), r1 * (float) Math.sin(angle + 3 * da), -width * 0.5f);
            glNormal3f((float) Math.cos(angle), (float) Math.sin(angle), 0.0f);
        }
        glVertex3f(r1 * (float) Math.cos(0), r1 * (float) Math.sin(0), width * 0.5f);
        glVertex3f(r1 * (float) Math.cos(0), r1 * (float) Math.sin(0), -width * 0.5f);
        glEnd();

        glShadeModel(GL_SMOOTH);

        /* draw inside radius cylinder */
        glBegin(GL_QUAD_STRIP);
        for (i = 0; i <= teeth; i++) {
            angle = i * 2.0f * (float) Math.PI / teeth;
            glNormal3f(-(float) Math.cos(angle), -(float) Math.sin(angle), 0.0f);
            glVertex3f(r0 * (float) Math.cos(angle), r0 * (float) Math.sin(angle), -width * 0.5f);
            glVertex3f(r0 * (float) Math.cos(angle), r0 * (float) Math.sin(angle), width * 0.5f);
        }
        glEnd();
    }
}
