import React, {
  useContext,
  useEffect,
  useState,
} from 'react';
import styles from '../../../../scss/ExchangeHistory.module.scss';
import MyPageCard from './MyPageCard';
import AuthContext from '../../../../utils/AuthContext';
import axios from 'axios';
import { API_BASE_URL } from '../../../../config/host-config';

const ExchangeHistory = () => {
  const { id } = useContext(AuthContext);
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    const fetchData = async () => {
      console.log('가지고 있는 id 값:', id);
      try {
        const res = await axios.post(
          API_BASE_URL + '/api/v2/exchange/' + id,
        );
        setHistory(res.data);
        console.log(res.data);
      } catch (error) {
        console.error('Error fetching data:', error);
      } finally {
        setLoading(false);
      }
    };

    // 비동기 함수 호출
    fetchData();
  }, [id]);

  if (loading) {
    return <div>Loading...</div>;
  }
  return (
    <>
      <h3 className={styles.myExchangeHeader}>
        Exchange History{' '}
      </h3>
      {history.map((item, key) => (
        <MyPageCard key={key} item={item} />
      ))}
    </>
  );
};

export default ExchangeHistory;
