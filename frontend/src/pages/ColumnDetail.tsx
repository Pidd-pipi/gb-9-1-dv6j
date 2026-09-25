import { useEffect, useState } from 'react'
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
  Alert,
  Space,
  Spin,
} from 'antd'
import { LockOutlined } from '@ant-design/icons'
import { columnApi } from '../api/column'
import type { Column, Article, SubscriptionStatus, SubscriptionPlan } from '../types'
import dayjs from 'dayjs'

const { Title, Text, Paragraph } = Typography

const PLAN_LABELS: Record<SubscriptionPlan, string> = {
  MONTHLY: '月付',
  QUARTERLY: '季付',
  YEARLY: '年付',
}

function ColumnDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [column, setColumn] = useState<Column | null>(null)
  const [articles, setArticles] = useState<Article[]>([])
  const [subStatus, setSubStatus] = useState<SubscriptionStatus | null>(null)
  const [loading, setLoading] = useState(false)
  const [subscribeModalVisible, setSubscribeModalVisible] = useState(false)
  const [selectedPlan, setSelectedPlan] = useState<SubscriptionPlan>('MONTHLY')
  const [subscribing, setSubscribing] = useState(false)

  useEffect(() => {
    if (id) {
      loadColumnDetail()
    }
  }, [id])

  useEffect(() => {
    if (searchParams.get('renew') === '1' && subStatus && column) {
      setSubscribeModalVisible(true)
      searchParams.delete('renew')
      setSearchParams(searchParams, { replace: true })
    }
  }, [searchParams, subStatus, column])

  const loadColumnDetail = async () => {
    if (!id) return
    setLoading(true)
    try {
      const [columnRes, articlesRes, statusRes] = await Promise.all([
        columnApi.getById(id),
        columnApi.getArticles(id),
        columnApi.getSubscriptionStatus(id),
      ])
      setColumn(columnRes.data?.data || columnRes.data)
      setArticles(articlesRes.data?.data?.content || articlesRes.data || [])
      setSubStatus(statusRes.data?.data || null)
    } catch (error) {
      console.error('Failed to load column:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSubscribe = async () => {
    if (!id) return
    setSubscribing(true)
    try {
      await columnApi.subscribe(id, selectedPlan)
      setSubscribeModalVisible(false)
      await loadColumnDetail()
    } catch (error) {
      console.error('Subscribe failed:', error)
    } finally {
      setSubscribing(false)
    }
  }

  const isRenew = subStatus?.status === 'ACTIVE' || subStatus?.status === 'EXPIRED'

  const planOptions: { label: string; value: SubscriptionPlan; price: number }[] = column
    ? [
        { label: PLAN_LABELS.MONTHLY, value: 'MONTHLY', price: column.monthlyPrice },
        { label: PLAN_LABELS.QUARTERLY, value: 'QUARTERLY', price: column.quarterlyPrice },
        { label: PLAN_LABELS.YEARLY, value: 'YEARLY', price: column.yearlyPrice },
      ]
    : []

  const renderStatusTag = () => {
    if (!subStatus || subStatus.status === 'NONE') {
      return <Tag color="default">未订阅</Tag>
    }
    if (subStatus.status === 'ACTIVE') {
      return <Tag color="green">订阅中 · {dayjs(subStatus.endDate).format('YYYY-MM-DD')} 到期</Tag>
    }
    return <Tag color="red">已过期 · 原到期 {dayjs(subStatus.endDate).format('YYYY-MM-DD')}</Tag>
  }

  if (loading || !column) {
    return <Spin style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />
  }

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
              {renderStatusTag()}
            </div>
            <Paragraph type="secondary">{column.description}</Paragraph>
            <Descriptions column={3} style={{ marginTop: 16 }}>
              <Descriptions.Item label="文章数">{column.articleCount}</Descriptions.Item>
              <Descriptions.Item label="订阅数">{column.subscriberCount}</Descriptions.Item>
              <Descriptions.Item label="月付价格" className="price-text">
                ¥{column.monthlyPrice}
              </Descriptions.Item>
            </Descriptions>
            <div style={{ marginTop: 24 }}>
              <Space size="middle">
                <Button
                  type="primary"
                  size="large"
                  onClick={() => setSubscribeModalVisible(true)}
                >
                  {subStatus?.status === 'ACTIVE'
                    ? '立即续费'
                    : subStatus?.status === 'EXPIRED'
                      ? '重新订阅'
                      : '立即订阅'}
                </Button>
                {subStatus?.status === 'ACTIVE' && (
                  <Text type="secondary">
                    续费后新时长将在 {dayjs(subStatus.endDate).format('YYYY-MM-DD')} 基础上叠加
                  </Text>
                )}
                {subStatus?.status === 'EXPIRED' && (
                  <Text type="secondary">已过期，续费将从当前时间重新起算</Text>
                )}
              </Space>
            </div>
          </div>
        </div>
      </Card>

      <Card title="文章列表" style={{ marginTop: 24 }}>
        <List
          dataSource={articles}
          renderItem={(article, index) => {
            const locked = article.readable === false
            return (
              <List.Item
                actions={[
                  <Button
                    type="link"
                    key="read"
                    onClick={() => navigate(`/columns/${id}/articles/${article.id}`)}
                  >
                    {locked ? '查看摘要' : '阅读'}
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
                      {article.readable === true && (
                        <Tag color="green" style={{ marginLeft: 12 }}>
                          可阅读
                        </Tag>
                      )}
                      {locked && (
                        <Tag
                          icon={<LockOutlined />}
                          color={article.lockReason === 'EXPIRED_AFTER' ? 'orange' : 'default'}
                          style={{ marginLeft: 12 }}
                        >
                          {article.lockReason === 'EXPIRED_AFTER'
                            ? '到期后更新 · 续费可读'
                            : '订阅后可读'}
                        </Tag>
                      )}
                    </span>
                  }
                  description={
                    <span>
                      <Text type="secondary" style={{ marginRight: 12 }}>
                        {dayjs(article.createdAt).format('YYYY-MM-DD')}
                      </Text>
                      {article.summary}
                    </span>
                  }
                />
              </List.Item>
            )
          }}
        />
      </Card>

      <Modal
        title={
          isRenew
            ? subStatus?.status === 'ACTIVE'
              ? '续费专栏'
              : '重新订阅专栏'
            : '选择订阅计划'
        }
        open={subscribeModalVisible}
        onOk={handleSubscribe}
        onCancel={() => setSubscribeModalVisible(false)}
        confirmLoading={subscribing}
        okText={subStatus?.status === 'ACTIVE' ? '确认续费' : '确认订阅'}
        cancelText="取消"
      >
        {subStatus?.status === 'ACTIVE' && (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message={`当前有效期至 ${dayjs(subStatus.endDate).format('YYYY-MM-DD')}，续费后新时长从该日期往后叠加`}
          />
        )}
        {subStatus?.status === 'EXPIRED' && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="订阅已过期，续费后从当前时间重新起算"
          />
        )}
        <Radio.Group
          value={selectedPlan}
          onChange={(e) => setSelectedPlan(e.target.value as SubscriptionPlan)}
          style={{ width: '100%' }}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {planOptions.map((option) => (
              <Radio value={option.value} key={option.value}>
                <Space size="large">
                  <span>
                    {option.label} ¥{option.price}
                  </span>
                  <Text type="secondary">
                    {subStatus?.status === 'ACTIVE' ? '续费后到期：' : '到期日：'}
                    {dayjs(subStatus?.planEndDates?.[option.value]).format('YYYY-MM-DD')}
                  </Text>
                </Space>
              </Radio>
            ))}
          </div>
        </Radio.Group>
      </Modal>
    </div>
  )
}

export default ColumnDetail
