const XBL_AUTH_URL = 'https://user.auth.xboxlive.com/user/authenticate';
const XSTS_AUTH_URL = 'https://xsts.auth.xboxlive.com/xsts/authorize';
const MC_LOGIN_URL = 'https://api.minecraftservices.com/authentication/login_with_xbox';
const MC_ENTITLEMENTS_URL = 'https://api.minecraftservices.com/entitlements/mcstore';
const MC_PROFILE_URL = 'https://api.minecraftservices.com/minecraft/profile';

async function requestJson(url, options) {
  const response = await fetch(url, options);
  const text = await response.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = { raw: text };
  }

  if (!response.ok) {
    const mapped = mapXstsError(body);
    const message = mapped || body.errorMessage || body.error || body.raw || response.statusText;
    throw new Error(message);
  }

  return body;
}

function mapXstsError(body) {
  const code = Number(body?.XErr || body?.xerr);
  if (!code) return '';

  const messages = {
    2148916233: 'This Microsoft account does not have an Xbox profile. Open xbox.com once, create a profile, then sign in again.',
    2148916235: 'Xbox Live is not available for this account region.',
    2148916236: 'This account needs adult verification before Xbox Live sign-in can continue.',
    2148916237: 'This account needs adult verification before Xbox Live sign-in can continue.',
    2148916238: 'This is a child account and cannot continue without family settings approval.'
  };

  return messages[code] || `Xbox XSTS authentication failed with code ${code}.`;
}

async function authenticateXboxLive(microsoftAccessToken) {
  return requestJson(XBL_AUTH_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json'
    },
    body: JSON.stringify({
      Properties: {
        AuthMethod: 'RPS',
        SiteName: 'user.auth.xboxlive.com',
        RpsTicket: `d=${microsoftAccessToken}`
      },
      RelyingParty: 'http://auth.xboxlive.com',
      TokenType: 'JWT'
    })
  });
}

async function authorizeXsts(userToken) {
  return requestJson(XSTS_AUTH_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json'
    },
    body: JSON.stringify({
      Properties: {
        SandboxId: 'RETAIL',
        UserTokens: [userToken]
      },
      RelyingParty: 'rp://api.minecraftservices.com/',
      TokenType: 'JWT'
    })
  });
}

async function loginMinecraftWithXbox(xsts) {
  const userHash = xsts.DisplayClaims?.xui?.[0]?.uhs;
  if (!userHash) {
    throw new Error('Xbox XSTS token did not include a user hash.');
  }

  return requestJson(MC_LOGIN_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json'
    },
    body: JSON.stringify({
      identityToken: `XBL3.0 x=${userHash};${xsts.Token}`
    })
  });
}

async function fetchMinecraftProfile(minecraftAccessToken) {
  return requestJson(MC_PROFILE_URL, {
    headers: {
      Authorization: `Bearer ${minecraftAccessToken}`,
      Accept: 'application/json'
    }
  });
}

async function fetchEntitlements(minecraftAccessToken) {
  return requestJson(MC_ENTITLEMENTS_URL, {
    headers: {
      Authorization: `Bearer ${minecraftAccessToken}`,
      Accept: 'application/json'
    }
  });
}

function hasJavaEntitlement(entitlements) {
  const items = entitlements?.items || [];
  return items.some((item) => item.name === 'game_minecraft' || item.name === 'product_minecraft');
}

async function authenticateMinecraft(microsoftAccessToken) {
  const xboxLive = await authenticateXboxLive(microsoftAccessToken);
  const xsts = await authorizeXsts(xboxLive.Token);
  const minecraft = await loginMinecraftWithXbox(xsts);
  const accessToken = minecraft.access_token;

  if (!accessToken) {
    throw new Error('Minecraft Services did not return an access token.');
  }

  const [entitlements, profile] = await Promise.all([
    fetchEntitlements(accessToken),
    fetchMinecraftProfile(accessToken)
  ]);

  if (!hasJavaEntitlement(entitlements)) {
    throw new Error('This Microsoft account does not own Minecraft: Java Edition.');
  }

  if (!profile?.id || !profile?.name) {
    throw new Error('Minecraft profile is missing. Make sure this account owns Java Edition and has a Java profile.');
  }

  return {
    accessToken,
    expiresAt: Date.now() + Number(minecraft.expires_in || 3600) * 1000,
    profile: {
      id: profile.id,
      name: profile.name,
      skins: profile.skins || [],
      capes: profile.capes || []
    },
    entitlements
  };
}

module.exports = {
  authenticateMinecraft
};
