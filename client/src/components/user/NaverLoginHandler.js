import React, { useEffect } from 'react';
import {
  API_BASE_URL,
  USER,
} from '../../config/host-config';

const NaverLoginHandler = () => {
  console.log('사용자가 naver인증서버에서 redirect 진행함');

  const REQUEST_URI = API_BASE_URL + USER;

  const code = new URL(
    window.location.href,
  ).searchParams.get('code');
  //
  useEffect(() => {
    const NaverLogin = async () => {
      console.log('code: ', code);
      const res = await fetch(
        REQUEST_URI + '/naverlogin?code=' + code,
      );
    };

    NaverLogin();
  }, []);

  return <div>NaverLoginHandler</div>;
};

export default NaverLoginHandler;
