import { useEffect, useMemo, useState } from 'react'
import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import {
  Card,
  Typography,
  Button,
  List,
  Tag,
  Avatar,
  Descriptions,
  Radio,
  Modal,
  message,
  Spin,
  Alert,
} from 'antd'
import { LockOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { columnApi } from '../api/column'
import type { Column, Article, Subscription } from '../types'
import dayjs, { Dayjs } from 'dayjs'

const { Title, Text, Paragraph } = Typography

type PlanType = 'MONTHLY' | 'QUARTERLY' | 'YEARLY'

function ColumnDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [column, setColumn] = useState<Column | null>(null)
  const [articles, setArticles] = useState<Article[]>([])
  const [subscription, setSubscription] = useState<Subscription | null>(null)
  const [loading, setLoading] = useState(false)
  const [subscribeModalVisible, setSubscribeModalVisible] = useState(false)
  const [selectedPlan, setSelectedPlan] = useState<PlanType>('MONTHLY')
  const [subscribing, setSubscribing] = useState(false)

  useEffect(() => {
    if (id) {
      loadColumnDetail()
    }
  }, [id])

  // 从“我的订阅”等页面带 renew=1 跳转过来时，自动打开续费弹窗
  useEffect(() => {
    if (searchParams.get('renew') === '1' && !loading) {
      setSubscribeModalVisible(true)
      searchParams.delete('renew')
      setSearchParams(searchParams, { replace: true })
    }
  }, [searchParams, loading])

  const loadColumnDetail = async () => {
    if (!id) return
    setLoading(true)
    try {
      const [columnRes, articlesRes, subscriptionRes] = await Promise.all([
        columnApi.getById(id),
        columnApi.getArticles(id),
        columnApi.getColumnSubscription(id),
      ])
      setColumn(columnRes.data?.data || columnRes.data)
      setArticles(articlesRes.data?.data || [])
      setSubscription(subscriptionRes.data?.data || null)
    } catch (error) {
      console.error('Failed to load column:', error)
    } finally {
      setLoading(false)
    }
  }

  const subscriptionActive = useMemo(
    () => !!subscription && dayjs(subscription.endDate).isAfter(dayjs()),
    [subscription]
  )

  // 续费时长从原到期日叠加；已过期或新订阅则从现在开始起算
  const renewBase: Dayjs = useMemo(
    () => (subscriptionActive ? dayjs(subscription!.endDate) : dayjs()),
    [subscriptionActive, subscription]
  )

  const planOptions = useMemo(() => {
    if (!column) return []
    const plans: Array<{ value: PlanType; label: string; price: number; endDate: Dayjs }> = [
      { value: 'MONTHLY', label: '月付', price: column.monthlyPrice, endDate: renewBase.add(1, 'month') },
      { value: 'QUARTERLY', label: '季付', price: column.quarterlyPrice, endDate: renewBase.add(3, 'month') },
      { value: 'YEARLY', label: '年付', price: column.yearlyPrice, endDate: renewBase.add(1, 'year') },
    ]
    return plans
  }, [column, renewBase])

  const handleSubscribe = async () => {
    if (!id) return
    setSubscribing(true)
    try {
      const res = await columnApi.subscribe(id, selectedPlan)
      const body = res.data
      if (body?.success === false) {
        message.error(body.message || '操作失败')
        return
      }
      message.success(body?.message || '订阅成功')
      setSubscribeModalVisible(false)
      // 刷新订阅状态和文章可读状态
      loadColumnDetail()
    } catch (error) {
      console.error('Subscribe failed:', error)
    } finally {
      setSubscribing(false)
    }
  }

  if (loading || !column) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

  const subscribeButtonText = !subscription
    ? '立即订阅'
    : subscriptionActive
      ? '续费'
      : '重新订阅'

  return (
    <div>
      <Card>
        <div style={{ display: 'flex', gap: 24 }}>
          <div
            style={{
              width: 240,
              height: 320,
              background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              borderRadius: 8,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#fff',
              fontSize: 96,
              flexShrink: 0,
            }}
          >
            📚
          </div>
          <div style={{ flex: 1 }}>
            <Title level={2}>{column.title}</Title>
            <div style={{ marginBottom: 16 }}>
              <Avatar icon={<span>👤</span>} src={column.creator?.avatar} />
              <Text style={{ marginLeft: 8 }}>{column.creator?.username}</Text>
              <Tag color="purple" style={{ marginLeft: 8 }}>
                {column.category}
              </Tag>
            </div>
            <Paragraph type="secondary">{column.description}</Paragraph>
            <Descriptions column={3} style={{ marginTop: 16 }}>
              <Descriptions.Item label="文章数">{column.articleCount}</Descriptions.Item>
              <Descriptions.Item label="订阅数">{column.subscriberCount}</Descriptions.Item>
              <Descriptions.Item label="月付价格" className="price-text">
                ¥{column.monthlyPrice}
              </Descriptions.Item>
            </Descriptions>
            {subscription && (
              <Alert
                style={{ marginTop: 16 }}
                type={subscriptionActive ? 'success' : 'warning'}
                showIcon
                message={
                  subscriptionActive
                    ? `订阅有效，到期时间：${dayjs(subscription.endDate).format('YYYY-MM-DD HH:mm')}`
                    : `订阅已于 ${dayjs(subscription.endDate).format('YYYY-MM-DD HH:mm')} 到期，到期前发布的文章仍可阅读，续费后可读全部文章`
                }
              />
            )}
            <div style={{ marginTop: 24 }}>
              <Button
                type="primary"
                size="large"
                onClick={() => setSubscribeModalVisible(true)}
              >
                {subscribeButtonText}
              </Button>
            </div>
          </div>
        </div>
      </Card>

      <Card title="文章列表" style={{ marginTop: 24 }}>
        <List
          dataSource={articles}
          renderItem={(article, index) => (
            <List.Item
              actions={[
                article.readable ? (
                  <Tag icon={<CheckCircleOutlined />} color="success" key="status">
                    可读
                  </Tag>
                ) : (
                  <Tag icon={<LockOutlined />} color="warning" key="status">
                    订阅可读
                  </Tag>
                ),
                <Button
                  type="link"
                  key="read"
                  onClick={() => navigate(`/columns/${id}/articles/${article.id}`)}
                >
                  阅读
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={
                  <span>
                    <Text type="secondary" style={{ marginRight: 12 }}>
                      #{index + 1}
                    </Text>
                    {article.title}
                  </span>
                }
                description={
                  <span>
                    {article.summary}
                    {article.createdAt && (
                      <Text type="secondary" style={{ marginLeft: 12, fontSize: 12 }}>
                        发布于 {dayjs(article.createdAt).format('YYYY-MM-DD')}
                      </Text>
                    )}
                  </span>
                }
              />
            </List.Item>
          )}
        />
      </Card>

      <Modal
        title={subscription ? '续费订阅' : '选择订阅计划'}
        open={subscribeModalVisible}
        onOk={handleSubscribe}
        onCancel={() => setSubscribeModalVisible(false)}
        confirmLoading={subscribing}
        okText={subscription ? '确认续费' : '确认订阅'}
        cancelText="取消"
      >
        {subscription && (
          <Alert
            style={{ marginBottom: 16 }}
            type="info"
            showIcon
            message={
              subscriptionActive
                ? `当前有效期至 ${dayjs(subscription.endDate).format('YYYY-MM-DD HH:mm')}，续费时长将从到期日往后叠加`
                : `订阅已于 ${dayjs(subscription.endDate).format('YYYY-MM-DD HH:mm')} 到期，续费将从现在重新起算`
            }
          />
        )}
        <Radio.Group
          value={selectedPlan}
          onChange={(e) => setSelectedPlan(e.target.value as PlanType)}
          style={{ width: '100%' }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {planOptions.map((option) => (
              <Radio value={option.value} key={option.value}>
                <span>
                  {option.label} ¥{option.price}
                  <Text type="secondary" style={{ marginLeft: 12, fontSize: 12 }}>
                    有效期至 {option.endDate.format('YYYY-MM-DD')}
                  </Text>
                </span>
              </Radio>
            ))}
          </div>
        </Radio.Group>
      </Modal>
    </div>
  )
}

export default ColumnDetail
