import React, {
  useEffect,
  useState,
  useContext,
} from 'react';
import Styles from '../../../scss/LiveRankExRateCard.module.scss';
import { Container } from 'reactstrap';
import { useNavigate } from 'react-router-dom';
import AuthContext from '../../../utils/AuthContext';
const RealTimeExchangesCard = (item) => {
  const navigate = useNavigate();
  const {
    changeRate,
    currencyCode,
    exchangeRate,
    flag,
    nationCode,
    nationName,
  } = item.item;
  const { onChangeNation } = useContext(AuthContext);
  const exRate = (c) => {
    if (changeRate > 0) {
      return (
        <div style={{ color: 'red', marginTop: '5px' }}>
          ▲ {Math.abs(c).toFixed(2)} {''}%
        </div>
      );
    } else if (changeRate < 0) {
      return (
        <div style={{ color: 'blue', marginTop: '5px' }}>
          ▼ {Math.abs(c).toFixed(2)} {''}%
        </div>
      );
    } else {
      return (
        <div style={{ color: 'gray', marginTop: '5px' }}>
          {Math.abs(c).toFixed(2)} {''}%
        </div>
      );
    }
  };

  const containerStyle = {
    width: '100%',
    height: '100%',
    display: 'flex',
    padding: '15px',
  };

  function removeInvalidChars(str) {
    return str.replace(/ï»¿/g, '');
  }
  const clickHandler = () => {
    onChangeNation(nationCode);
    navigate('/main');
  };
  return (
    <Container
      onClick={clickHandler}
      style={containerStyle}
    >
      <div className={Styles.box1}>
        <h6>
          {nationName} {currencyCode}
        </h6>
        <h4>{exchangeRate} 원</h4>
        <h5>{exRate(changeRate)}</h5>
      </div>
      <div className={Styles.box2}>
        <img src={removeInvalidChars(atob(flag))} />
      </div>
    </Container>
  );
};

export default RealTimeExchangesCard;
